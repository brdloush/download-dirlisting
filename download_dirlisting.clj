#!/usr/bin/env bb

(ns download-dirlisting
  (:require  [babashka.fs :as fs]
             [babashka.pods :as pods]
             [clojure.string :as str]
             [clojure.tools.cli :refer [parse-opts]]
             [clojure.zip :as z]
             [org.httpkit.client :as http]
             [org.httpkit.sni-client :as sni-client])
  (:import [java.util.concurrent Executors]))

(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

(pods/load-pod 'retrogradeorbit/bootleg "0.1.9")

(require
 '[pod.retrogradeorbit.bootleg.utils :as bootleg]
 '[pod.retrogradeorbit.hickory.select :as s])


(defn fetch-as-hickory [base-url path]
  (let [url (str base-url path)
        _ (println " -" url)]
    (-> @(http/get url)
        :body
        (bootleg/convert-to :hickory))))

(defn el->href [el]
  (-> el :attrs :href))

(defn parse-folder-hickory [folder-hickory]
  (let [subdirs (->> (s/select (s/and (s/tag :a)
                                      (s/attr :href #(.endsWith % "/")))
                               folder-hickory)
                     (remove #(str/includes? (str (:content %)) "To Parent Directory"))
                     (map el->href)
                     (map #(-> {:type :dir
                                :path %})))
        files (->> (s/select (s/and (s/tag :a)
                                    (s/attr :href #(not (.endsWith % "/"))))
                             folder-hickory)
                   (map el->href)
                   (map #(-> {:type :file
                              :path %})))]
    (into [] (concat subdirs files))))

(defn fetch-url-items [base-url path]
  (parse-folder-hickory (fetch-as-hickory base-url path)))

(defn is-dir? [{:keys [type]}]
  (= type :dir))

(defn file-zipper [root-httpftp-url input-dir]
  (z/zipper
   is-dir?
   (fn children [node]
     (fetch-url-items root-httpftp-url (:path node)))
   (fn make-node [_ c]
     c)
   input-dir))

(defn find-files-to-download [root-httpftp-url base-path]
  (->> (file-zipper root-httpftp-url {:type :dir, :path base-path})
       (iterate z/next)
       (take-while #(not (z/end? %)))
       (map first)
       (remove is-dir?)))

(defn split-path [file-path]
  (let [splitted (str/split file-path #"/")]
    [(str/join "/" (drop-last splitted))
     (last splitted)]))

(defn download-file [root-httpftp-url base-path {:keys [path]} target-root-dir]
  (let [[dir-path filename] (split-path path)
        response @(http/get (str root-httpftp-url path))
        relative-dir-path (str/replace-first dir-path base-path "")]
    (fs/create-dirs (str target-root-dir relative-dir-path))
    (with-open [fos (->> (str target-root-dir "/" relative-dir-path "/" filename)
                         (java.io.FileOutputStream.)
                         (java.io.BufferedOutputStream.))]
      (let [body-is (:body response)]
        (.transferTo body-is fos)))))

(defn download-files [root-httpftp-url base-path target-dir threads]
  (let [_ (println "Discovering files to be downloaded")
        files (find-files-to-download root-httpftp-url base-path)
        _ (println "Found" (count files) "files to be downloaded using" threads "threads")]
    (time
     (let [total-files (count files)
           count-atom (atom 0)
           report-every 500
           _ (add-watch count-atom
                        :report-finished-files
                        (fn [_ _ _ new-count]
                          (when (= (mod new-count report-every) 0)
                            (println "Downloaded" new-count "files"))
                          (when (= new-count total-files)
                            (println (format "All files (%s) done" total-files)))))
           pool  (Executors/newFixedThreadPool threads)
           tasks (->> files
                      (map (fn [file-item]
                             (fn []
                               (download-file root-httpftp-url base-path file-item target-dir)
                               (swap! count-atom inc)))))]
       (doseq [future (.invokeAll pool tasks)]
         (.get future))
       (.shutdown pool)))))

(def cli-options
  [["-b" "--base-url BASEURL" "Base URL without path"
    :validate [#(not-empty %) "Must be non-empty string"]]
   ["-p" "--path PATH" "Relative path to start download at"]
   ["-d" "--target-dir TARGETDIR" "A directory where files/subdirectories will be stored to"]
   ["-t" "--threads THREADS" "Number of parallel threads for simultanous download"
    :default 50
    :parse-fn parse-long]
   ["-h" "--help" "Shows this usage information."]])

(:options (parse-opts *command-line-args* cli-options))

(defn print-sample-usage []
  (println "\n\nexample: ./download_dirlisting.clj -b https://portal.cisjr.cz -p /pub/draha/celostatni/szdc/2022 -d /tmp/2022 -t 100"))

(defn print-app-purporse []
  (println
   "download_dirlisting.clj
    
Babashka script allowing you to mass-download files/directories off a file listing pages accessible via http(s), 
such as common \"ftp-listing\" pages served via http.
    
The folders/files are download from the starting URI build from BASE_URL+PATH. 
    
Folder structure after that starting URL is preserved even in target download folder. 
    
Since the (sub-)folders may contain many small files, downloads can be performed simultanously using multiple threads."))

(let [{:keys [options summary errors]} (parse-opts *command-line-args* cli-options)
      {:keys [base-url path target-dir threads help]} options]
  (try
    (cond
      (or help (empty? *command-line-args*))
      (do (print-app-purporse)
          (println summary)
          (print-sample-usage)
          (System/exit 0))

      errors
      (do (println "Errors in command line arguments: " errors)
          (System/exit 1))

      :else
      (download-files base-url path target-dir threads))

    (catch Exception e
      (println e)
      (System/exit 1))))

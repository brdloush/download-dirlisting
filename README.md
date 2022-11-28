# download-dirlisting.clj

This babashka script offers fast mass-download of files from a remote directory listing pages
served via HTTP(s). 

The script was created because [GVD2022](https://github.com/gtfscr/GVD2022) was downloading many tiny files using following command: 
```bash
wget --remote-encoding=cp1250 -r -N --cut-dirs=2 -nH --accept="zip,ZIP" --reject-regex='2021-12/*|2022-01/*' ftp://ftp.cisjr.cz/draha/celostatni/szdc/2022
```

Unfortunately downloading over 30k small files using that approach took more than 40 minutes on good internet connection, because even as small latency as 100ms quickly sums up into 40 minutes when things are executed sequentially.

On contrary, `download-dirlisting.clj` can do the same task in 20s in total (instead of over 40 minutes).

```bash
➜ time ./download_dirlisting.clj -b https://portal.cisjr.cz -p /pub/draha/celostatni/szdc/2022 -d /tmp/2022 -t 100
Discovering files to be downloaded
- https://portal.cisjr.cz/pub/draha/celostatni/szdc/2022
...redacted...
- https://portal.cisjr.cz/pub/draha/celostatni/szdc/2022/2022-11/
Found 32627 files to be downloaded using 100 threads
Downloaded 500 files
Downloaded 1000 files
...redacted...
Downloaded 32500 files
All files (32627) done
"Elapsed time: 13877.40301 msecs"
./download_dirlisting.clj -b https://portal.cisjr.cz -p  -d /tmp/2022 -t 100  20.49s user 4.54s system 124% cpu 20.134 total
```

# Usage

```bash
download_dirlisting.clj
    
Babashka script allowing you to mass-download files/directories off a file listing pages accessible via http(s), 
such as common "ftp-listing" pages served via http.
    
The folders/files are download from the starting URI build from BASE_URL+PATH. 
    
Folder structure after that starting URL is preserved even in target download folder. 
    
Since the (sub-)folders may contain many small files, downloads can be performed simultanously using multiple threads.
  -b, --base-url BASEURL          Base URL without path
  -p, --path PATH                 Relative path to start download at
  -d, --target-dir TARGETDIR      A directory where files/subdirectories will be stored to
  -t, --threads THREADS       50  Number of parallel threads for simultanous download
  -h, --help                      Shows this usage information.


example: ./download_dirlisting.clj -b https://portal.cisjr.cz -p /pub/draha/celostatni/szdc/2022 -d /tmp/2022 -t 100
```

# Requirements

- babashka
- internet connectivity as the script also downloads its dependency as a babashka pod

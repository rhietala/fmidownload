(defproject fmidownload "0.1.0-SNAPSHOT"
  :description "A simple client for FMI open data"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.xml "0.0.7"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/data.csv "0.1.3"]
                 [clj-http "2.0.0"]
                 [clj-time "0.11.0"]]
  :plugins [[cider/cider-nrepl "0.10.0-SNAPSHOT"]]
  :main fmidownload.core)

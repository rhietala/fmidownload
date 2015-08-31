(ns fmidownload.core
  (:require [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [clojure.data.xml :as xml]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(def APIKEY (clojure.string/trim-newline (slurp "apikey.txt")))
(def BASEURL "http://data.fmi.fi/fmi-apikey/")
(def PATH "/wfs/eng")
(def PARAMS {:request "getFeature"
             :storedquery_id "fmi::observations::radiation::timevaluepair"
             :parameters "glob_1min"
             :timestep "60"})

;; XML-related functions

(defn parse-position
  "Given a string (s) in format '59.78423 21.36783 ', parse them to two floats
  and return them as a sequence (59.78423 21.36783). Data will be lat-long
  coordinates."
  [s]
  (map read-string (rest (re-find #"([0-9]+\.[0-9]+) ([0-9]+\.[0-9]+)" s))))

(defn get-time-and-value
  "Parse timestamp and value from XML node (node), return a map"
  [node]
  {:timestamp (f/parse
               (f/formatters :date-time-no-ms)
               (zx/xml1->
                (zip/xml-zip node)
                first
                :time
                zx/text))
   :value (read-string (zx/xml1->
                        (zip/xml-zip node)
                        first
                        :value
                        zx/text))})

(defn get-results
  "Traverse XML from (node) to result nodes and get their timestamps and values,
  return a sequence of maps."
  [node]
  (zx/xml->
   (zip/xml-zip node)
   first
   :PointTimeSeriesObservation
   :result
   :MeasurementTimeseries
   :point
   :MeasurementTVP
   get-time-and-value))

(defn get-member-location
  "Given an XML node for measurement location (node), parse its name and position,
  return a map"
  [node]
  (let [name (zx/xml1->
              (zip/xml-zip node)
              first
              :PointTimeSeriesObservation
              :featureOfInterest
              :SF_SpatialSamplingFeature
              :shape
              :Point
              :name
              zx/text)
        pos (zx/xml1->
             (zip/xml-zip node)
             first
             :PointTimeSeriesObservation
             :featureOfInterest
             :SF_SpatialSamplingFeature
             :shape
             :Point
             :pos
             zx/text)]
    {:name name
     :pos (parse-position pos)}))

(defn parse-member
  "Given a measurement XML node (node), parse its name, position and measurement
  results. Return a map."
  [node]
  (merge (get-member-location node) {:results (get-results node)}))

(defn get-time
  "Return :starttime and :endtime parameters as one week periods counting
  backwards from today midnight with (week-offset), where 0 is previous week,
  1 is the one before that, etc."
  [week-offset]
  (let [n (t/now)
        now (t/date-time (t/year n) (t/month n) (t/day n))]
    {:starttime (f/unparse (f/formatters :date-time-no-ms)
                           (t/minus now (t/weeks (+ week-offset 1))))
     :endtime (f/unparse (f/formatters :date-time-no-ms)
                         (t/minus now (t/weeks week-offset)))}))

(defn get-radiation
  "Given (week-offset), query for FMI data with constant parameters
  BASEURL, APIKEY, PATH and PARAMS."
  [week-offset]
  (client/get (str BASEURL APIKEY PATH)
              {:query-params (merge PARAMS (get-time week-offset))}))

(defn get-zip
  "Given (week-offset), return xml-zip root node of FMI data."
  [week-offset]
  (zip/xml-zip (xml/parse-str ((get-radiation week-offset) :body))))

(defn download-fmi-data
  "Given (week-offset), return a vector of measurement locations (as maps)
  with :name, :pos and :results keys."
  [week-offset]
  (map parse-member (zx/xml->
                     (get-zip week-offset)
                     :member)))

(defn format-timestamp
  "Given a map (x) with :timestamp as clj-time object, return formatted time
  as string."
  [x]
  (f/unparse (f/formatter "yyyy-MM-dd HH:mm:ss")
             (:timestamp x)))

(defn format-timestamps
  "Given a location data map (loc-data), return all its measurement times as
  a vector of formatted time strings"
  [loc-data]
  (vec (sort (map format-timestamp
                  (:results loc-data)))))

(defn format-for-excel
  "Given a vector of location data sets (data), format data for excel import.
  Write an UTF-8 formatted CSV file (filename) so that measurement locations
  are in columns and results are in rows with first column being the measurement
  time"
  [data filename]
  (let [timestamps (format-timestamps (first data))
        parsed (mapv (fn [loc]
                       {:name (:name loc)
                        :lat (:lat loc)
                        :lon (:lon loc)
                        :results (map (fn [x] {:timestamp (format-timestamp x)
                                               :value (:value x)})
                                      (:results loc))})
                     data)
        csvheader (cons "" (mapv #(:name %) parsed))
        csvlat (cons "latitude" (mapv #(:lat %) parsed))
        csvlon (cons "longitude" (mapv #(:lon %) parsed))
        csvresults (mapv (fn [timestamp]
                           (cons timestamp
                                 (mapv (fn [loc]
                                         (:value
                                          (first
                                           (filter #(= timestamp (:timestamp %))
                                                   (:results loc)))))
                                       parsed)))
                         timestamps)]
    (with-open [out-file (io/writer filename)]
      (csv/write-csv
       out-file
       (vec (concat [csvheader csvlat csvlon] csvresults))))))

(defn merge-data
  "Given a vector of polled fmi-data sets (data), as only one week can be queried at
  a time, merge the data sets so that all data for each measurement location is
  in the same result vector, sorted by time"
  [data]
  (let [names (map #(:name %) (first data))]
    (map (fn [name]
           (let [pos (:pos (first (filter #(= name (:name %)) (first data))))]
           {:name name
            :lat (str (first pos))
            :lon (str (second pos))
            :results (sort-by :timestamp t/before?
                      (flatten (map #(:results %)
                                    (filter #(= name (:name %))
                                            (flatten data)))))}))
         names)))

(defn -main
  "Entry point, query FMI for data from (num-weeks) and write measurement results
  to (filename)"
  [num-weeks filename]
  (let [weeks (range (read-string num-weeks))
        weekly-data (map download-fmi-data weeks)
        merged (merge-data weekly-data)]
    (format-for-excel merged filename)))

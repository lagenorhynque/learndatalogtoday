(ns learndatalogtoday.handler
  (:require [clojure.edn :as edn] 
            [learndatalogtoday.views :as views]
            [compojure.core :refer [routes GET POST]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [datomic.api :as d]
            [hiccup.page :refer [html5]]))

(defn edn-response [edn-data]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str edn-data)})

(declare read-chapter read-chapter-data)

(defn app-routes [db chapters]
  (routes
   (GET "/" [])
   
   (GET ["/chapter/:n" :n #"[0-9]+"]
     [n]
     ;; Production
     #_(chapters (Integer/parseInt n))
     ;; Development
     (read-chapter (Integer/parseInt n)))
   
   (POST ["/query/:chapter/:exercise" :chapter #"[0-9]+" :exercise #"[0-9]+"]
     {{:keys [chapter exercise data] :as params} :params}
     (try 
       (let [chapter (Integer/parseInt chapter)
             exercise (Integer/parseInt exercise)
             usr-input (edn/read-string data)
             ;;   Prod (get-in chapter-data [chapter :exercises exercise :inputs])
             ans-input (get-in (read-chapter-data chapter) [:exercises exercise :inputs])
             [ans-query & ans-args] (map #(or (:correct-value %1) %2) ans-input usr-input)
             [usr-query & usr-args] (edn/read-string data)
             usr-result (apply d/q usr-query db usr-args)
             ans-result (apply d/q ans-query db ans-args)]
         (println "ans-query" ans-query)
         (println "usr-query" usr-query)
         (if (= usr-result ans-result)
           (edn-response {:status :success
                          :result usr-result})
           (edn-response {:status :fail
                          :result usr-result
                          :correct-result ans-result})))
       (catch Exception e
         (edn-response {:status :error
                        :message (.getMessage e)}))))
   
   (route/resources "/")
   (route/not-found "Not Found")))

(defn init-db [name schema seed-data]
  (let [uri (str "datomic:mem://" name)
        conn (do (d/delete-database uri)
                 (d/create-database uri)
                 (d/connect uri))]
    @(d/transact conn schema)
    @(d/transact conn seed-data)
    (d/db conn)))

(defn read-file [s]
  (read-string (slurp s)))

;; TODO: edn/read-file
(defn read-chapter-data [chapter]
  (->> chapter
       (format "resources/chapters/chapter-%s.edn") 
       read-file))

(defn read-chapter 
  "Returns a html string"
  [chapter]
  (let [chapter-data (read-chapter-data chapter)]
    (views/chapter-response (assoc chapter-data
                              :chapter chapter))))

(def app
  (let [schema (read-file "resources/db/schema.edn")
        seed-data (read-file "resources/db/data.edn")
        db (init-db "movies" schema seed-data)
        chapters (mapv read-chapter [0])] 
    (handler/site (app-routes db chapters))))


(get-in (read-chapter 0) [:exercises])
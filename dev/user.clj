(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [com.palletops.leaven :as leaven]
            [msg-fileservice.core :as core]))

(def system nil)


(defn init []
  (alter-var-root #'system
                  (constantly (core/server (core/service-definition)))))

(defn start []
  (alter-var-root #'system
                  leaven/start))

(defn stop []
  (alter-var-root #'system
                  (fn [s] (when s (leaven/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (repl/refresh :after 'user/go))

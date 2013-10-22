(defproject monitmonit "1.0.0"
  :description "Web service to monitor and control servers managed with monit."
  :url "https://github.com/dsabanin/monitmonit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [io.pedestal/pedestal.service "0.2.1"]
                 [io.pedestal/pedestal.service-tools "0.2.1"]

                 ;; Remove this line and uncomment the next line to
                 ;; use Tomcat instead of Jetty:
                 [io.pedestal/pedestal.jetty "0.2.1"]
                 ;; [io.pedestal/pedestal.tomcat "0.2.1"]
                 [hiccup "1.0.4"]
                 [simpleconf "1.0.0"]
                 [org.clojars.hozumi/clj-commons-exec "1.0.5"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :aliases {"run-dev" ["trampoline" "run" "-m" "monitmonit.server/run-dev"]}
  :repl-options  {:init-ns user
                  :init (try
                          (use 'io.pedestal.service-tools.dev)
                          (require 'monitmonit.service)
                          ;; Nasty trick to get around being unable to reference non-clojure.core symbols in :init
                          (eval '(init monitmonit.service/service #'monitmonit.service/routes))
                          (catch Throwable t
                            (println "ERROR: There was a problem loading io.pedestal.service-tools.dev")
                            (clojure.stacktrace/print-stack-trace t)
                            (println)))
                  :welcome (println "Welcome to pedestal-service! Run (tools-help) to see a list of useful functions.")}
  :main monitmonit.server)

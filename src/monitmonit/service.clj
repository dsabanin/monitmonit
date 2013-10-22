(ns monitmonit.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [ring.util.response :as ring-resp]
              [hiccup.core :refer :all]
              [hiccup.page :refer :all]
              [simpleconf.core :as config]
              [clj-commons-exec :as exec]))

(def ^:dynamic *config* (config/read-clojure "config.clj"))

(defn run-off [cmd]
  @(exec/sh ["/bin/sh" "-c" cmd] {:watchdog (:cmd-timeout *config*)}))

(declare control-panel)

(defn title []
  (str (:name *config*) " Service Dashboard"))

(defn layout
  [req & body]
  (let [auto-refresh? (= (get-in req [:query-params :refresh]) "true")]
    (html5
     [:head
      [:title (title)]
      (include-js "//code.jquery.com/jquery.js"
                  "//netdna.bootstrapcdn.com/bootstrap/3.0.0/js/bootstrap.min.js")
      (include-css "//netdna.bootstrapcdn.com/bootstrap/3.0.0/css/bootstrap.min.css")
      (when auto-refresh?
          [:meta {:content "30", :http-equiv "refresh"}])]
     [:body
      [:div.navbar.navbar-static-top.navbar-inverse
       {:role "navigation"}
       [:div.container
        [:div.navbar-header
         [:button.navbar-toggle
          {:data-target ".navbar-collapse",
           :data-toggle "collapse",
           :type "button"}
          [:span.icon-bar]
          [:span.icon-bar]
          [:span.icon-bar]]
         (control-panel auto-refresh?)
         [:a.navbar-brand {:href "#"} (title)]]]]
      [:div.container
       body
       [:div.footer
        [:hr]
        [:p "served by " [:a {:href "https://github.com/dsabanin/monitmonit"} "monitmonit"]
         " © " [:a {:href "http://dsabanin.com"} "dsabanin"] " 2013"]
        [:br]]]])))

(defn ssh
  [node cmd]
  (run-off (format (:ssh-template *config*) node cmd)))

(defn monit
  [node cmd]
  (ssh node (format (:monit-template *config*) cmd)))

(defn uptime
  [node]
  (ssh node "uptime"))

(defn parse-monit-summary
  [sum]
  (->> sum
       (re-seq #"(?m)^\s*Process '(.*?)'\s+(.*?)$")
       (map rest)
       (flatten)
       (apply hash-map)))

(defn monit-summary
  [node]
  (try
    (let [res (monit node "summary")]
      (if (= (:exit res) 0)
        (parse-monit-summary (:out res))
        (or (:err res) (:exception res))))
    (catch org.apache.commons.exec.ExecuteException exc
      exc)))

(def bad-states #{"does not exist"
                  "execution failed"
                  "execution failed - restart pending"
                  "execution failed - start pending"})
(def good-states #{"running"})

(def off-states #{"initializing"
                  "not monitored"
                  "running - stop pending"
                  "running - restart pending"})

(defn summary-in
  [summary group]
  (some group (map clojure.string/lower-case (vals summary))))

(defn summary-bad?
  [summary]
  (if (map? summary)
    (summary-in summary bad-states)
    true))

(defn summary-off?
  [summary]
  (if (map? summary)
    (summary-in summary off-states)
    true))

(defn status-btn
  [class node process status]
  [:div.btn-group
    [:button.btn.btn-xs.dropdown-toggle
     {:data-toggle "dropdown", :type "button", :class class}
     status
     "\n"
     [:span.caret]]
    [:ul.dropdown-menu
     {:role "menu"}
     [:li [:a {:href (format "/run?cmd=start&node=%s&process=%s" node process)} "Start"]]
     [:li [:a {:href (format "/run?cmd=stop&node=%s&process=%s" node process)} "Stop"]]
     [:li [:a {:href (format "/run?cmd=restart&node=%s&process=%s" node process)} "Restart"]]]])

(defn render-status
  [node process status]
  (let [status (.toLowerCase status)]
    (cond
     (good-states status) (status-btn "btn-success" node process status)
     (bad-states status) (status-btn "btn-danger" node process status)
     :else (status-btn "btn-default" node process status))))

(defn render-process
  [node [process status]]
  [:tr
   [:td process]
   [:td (render-status node process status)]])

(defn render-summary
  [node summary]
  (if (map? summary)
    [:table.table.table-striped {:id "accordion"}
     (->> summary
          (into (sorted-map))
          (map (partial render-process node)))]
    [:p summary]))

(defn top-button
  [name href kind]
  [:a.btn.navbar-btn.btn-xs {:class (str "btn-" kind)
                             :type "button"
                             :href href} name])

(defn render-node
  [node]
  (let [summary (future (monit-summary node))
        uptime (future (uptime node))
        node-id (str "collapse-" node)]
    [:div.panel {:class (if (summary-bad? @summary)
                          "panel-danger"
                          (if (summary-off? @summary)
                            "panel-default"
                            "panel-success"))}
     [:div.panel-heading [:a.accordion-toggle {:data-toggle "collapse"
                                               :data-parent "#accordion"
                                               :href (str "#" node-id)}
                          [:h3.panel-title
                           node
                           (when (map? @summary)
                             [:span
                              " "
                              (format "(%d)" (count @summary))])]]
      (top-button "start" (str "/run?cmd=start&node=" node) "default")
      " "
      (top-button "restart" (str "/run?cmd=restart&node=" node) "default")
      " "
      (top-button "stop" (str "/run?cmd=stop&node=" node) "default")]
     [:div.panel-collapse.collapse.out.nodes {:id node-id}
      [:div.panel-body
       (render-summary node @summary)]
      [:div.panel-footer
       [:small (clojure.string/replace-first (str (:out @uptime)) #",\s*load" "<br />load")]]]]))

(defn nav-button
  [name href]
  [:a.btn.btn-default.navbar-btn.btn-sm {:href href} name])

(defn all-action-btn
  [class name href confirm]
  [:a.btn.navbar-btn.btn-sm
   {:class class
    :href href
    :onclick (format "return confirm('%s')" confirm)}
   name])

(defn control-panel
  [auto-refresh?]
  (html (nav-button "Expand All" "javascript: $('.nodes').collapse('show');")
        " "
        (nav-button "Collapse All" "javascript: $('.nodes').collapse('hide');")
        " "
        (nav-button "Refresh" "/")
        " "
        (if auto-refresh?
          (nav-button "Stop Auto-Refresh" "/")
          (nav-button "Auto-Refresh" "/?refresh=true"))
        " "
        (all-action-btn "btn-success"
                          "Start All" "/run?cmd=start"
                          (str "Are you sure you want to start all servers in "
                               (:name *config*) "?"))
        " "
        (all-action-btn "btn-danger"
                          "Stop All" "/run?cmd=stop"
                          (str "Are you sure you want to stop all servers in "
                               (:name *config*) "?"))
        " "
        (all-action-btn "btn-danger"
                          "Restart All" "/run?cmd=restart"
                          (str "Are you sure you want to restart all servers in "
                               (:name *config*) "?"))))

(defn group-action-btn
  [class name href confirm]
  [:a.btn.btn-xs
   {:class class
    :href href
    :onclick (format "return confirm('%s')" confirm)}
   name])

(defn render-group
  [[group nodes]]
  (html
   [:h3 group
    " "
    (group-action-btn "btn-default"
                    "Start All" (format "/run?cmd=start&group=%s" group)
                    (str "Are you sure you want to start all " group " servers in "
                         (:name *config*) "?"))
    " "
    (group-action-btn "btn-default"
                    "Stop All" (format "/run?cmd=stop&group=%s" group)
                    (str "Are you sure you want to stop all " group " servers in "
                         (:name *config*) "?"))
    " "
    (group-action-btn "btn-default"
                    "Restart All" (format "/run?cmd=restart&group=%s" group)
                    (str "Are you sure you want to restart all " group " servers in "
                         (:name *config*) "?"))]
   (->> nodes
        (map #(future (render-node %)))
        (doall)
        (map deref)
        (partition-all 3)
        (mapcat (fn [nodes]
                  (vector [:div.row (map #(vector :div.col-md-4 %) nodes)]))))))

(defn dashboard
  [request]
  (ring-resp/response
   (layout request
           (->> (:nodes *config*)
                (partition 2)
                (map render-group)))))

(defn validate-cmd
  [cmd]
  (#{"restart" "start" "stop"} cmd))

(defn validate-node
  [node]
  ((into #{} (:nodes *config*)) node))

(defn validate-process
  [node process]
  ((into #{} (keys (monit-summary node))) process))

(defn monit-all
  [cmd nodes]
  (->> nodes
       (pmap #(prn (monit % (str cmd " all"))))
       (doall)))

(defn run [req]
  (when-let [cmd (validate-cmd (get-in req [:query-params :cmd]))]
    (if-let [node (validate-node (get-in req [:query-params :node]))]
      (if-let [process (validate-process node (get-in req [:query-params :process]))]
        (prn (monit node (str cmd " " process)))
        (prn (monit node (str cmd " all"))))
      (if-let [group (get-in req [:query-params :group])]
        (if-let [group-nodes (get (apply hash-map (:nodes *config*)) group)]
          (monit-all cmd group-nodes))
        (->> (apply hash-map (:nodes *config*))
             (vals)
             (flatten)
             (monit-all cmd)))))
  (ring-resp/redirect "/"))

(defroutes routes
  [[["/" {:get dashboard}
     ;; Set default interceptors for /about and any other paths under /
     ^:interceptors [(body-params/body-params) bootstrap/html-body]
     ["/run" {:get run}]]]])

;; You can use this fn or a per-request fn via io.pedestal.service.http.route/url-for
(def url-for (route/url-for-routes routes))

;; Consumed by monitmonit.server/create-server
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; :bootstrap/interceptors []
              ::bootstrap/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::bootstrap/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty or :tomcat (see comments in project.clj
              ;; to enable Tomcat)
              ;;::bootstrap/host "localhost"
              ::bootstrap/type :jetty
              ::bootstrap/port (:port *config* 8080)
              ::bootstrap/host (:host *config* "0.0.0.0")})

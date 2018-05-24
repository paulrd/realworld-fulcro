(ns conduit.intro
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.routing :as r]
            [conduit.ui.article-preview :as preview]
            [conduit.ui.other :as other]
            [conduit.handler.mutations :as mutations]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.network :as net]
            [fulcro.client.primitives :as prim :refer [defsc]]
            [fulcro.client.cards :refer [defcard-fulcro]]
            [conduit.ui.home :as home]
            [fulcro.client.dom :as dom]
            [conduit.ui.editor :as editor]
            [conduit.ui.account :as account]
            [conduit.ui.article :as article]
            [conduit.ui.profile :as profile]))

(r/defrouter TopRouter :router/top
  (fn [this props]
    (let [screen-name   (:screen props)
          screen-id-key (case screen-name
                          (:screen/editor :screen/article)
                          :article-id

                          :screen/profile
                          :user-id

                          :screen-id)
          screen-id (get props screen-id-key)]
      [screen-name screen-id]))
  :screen/home     home/Home
  :screen/settings account/SettingScreen
  :screen/editor   editor/EditorScreen
  :screen/log-in   account/LogInScreen
  :screen/sign-up  account/SignUpScreen
  :screen/article  article/ArticleScreen
  :screen/profile  profile/ProfileScreen)

(def ui-top (prim/factory TopRouter))

(def routing-tree
  (r/routing-tree
    (r/make-route :screen/home
      [(r/router-instruction :router/top [:screen/home :top])])

    (r/make-route :screen/editor
      [(r/router-instruction :router/top [:screen/editor :param/article-id])])

    (r/make-route :screen/article
      [(r/router-instruction :router/top [:screen/article :param/article-id])])

    (r/make-route :screen/settings
      [(r/router-instruction :router/top [:screen/settings :top])])
    (r/make-route :screen/sign-up
      [(r/router-instruction :router/top [:screen/sign-up :top])])
    (r/make-route :screen/log-in
      [(r/router-instruction :router/top [:screen/log-in :top])])

    (r/make-route :screen.feed/global
      [(r/router-instruction :router/top [:screen/home :top])
       (r/router-instruction :router/feeds [:screen.feed/global :top])])
    (r/make-route :screen.feed/personal
      [(r/router-instruction :router/top [:screen/home :top])
       (r/router-instruction :router/feeds [:screen.feed/personal :top])])

    (r/make-route :screen.profile/owned-articles
      [(r/router-instruction :router/top [:screen/profile :param/user-id])
       (r/router-instruction :router/profile [:screen.profile/owned-articles :param/user-id])])
    (r/make-route :screen.profile/liked-articles
      [(r/router-instruction :router/top [:screen/profile :param/user-id])
       (r/router-instruction :router/profile [:screen.profile/liked-articles :param/user-id])])))

(defn go-to-home [this]
  (prim/transact! this `[(r/route-to {:handler :screen/home})]))

(defsc Root [this {router :router/top :as props}]
  {:initial-state (fn [params] (merge routing-tree
                                 {:article/by-id {:none #:article {:id          :none
                                                                   :body        ""
                                                                   :title       ""
                                                                   :slug        ""
                                                                   :description ""
                                                                   :comments    [#:comment{:id :none :body "" :author {:user/id :guest}}]
                                                                   :author      {:user/id :guest}}}
                                  :user/whoami   #:user {:id    :guest
                                                         :name  "Guest"
                                                         :image "https://static.productionready.io/images/smiley-cyrus.jpg"
                                                         :email "non@exist"}}
                                 {:router/top (prim/get-initial-state TopRouter {})}))
   :query         [{:router/top (prim/get-query TopRouter)}
                   {:user/whoami (prim/get-query other/UserTinyPreview)}]}
  (dom/div
    (home/ui-nav-bar)
    (ui-top router)
    (home/ui-footer)))

(def token-store (atom "No token"))

(defn wrap-remember-token [res]
  (when-let [new-token (-> (:body res) (get :user/whoami) :app/token)]
    ;;(println (str "found token: " new-token))
    (reset! token-store (str "Token " new-token)))
  res)

(defn wrap-with-token [req]
  (assoc-in req [:headers "Authorization"] @token-store))

(defcard-fulcro yolo
  Root
  {} ; initial state. Leave empty to use :initial-state from root component
  {:inspect-data true
   :fulcro       {:reconciler-options {:shared-fn #(select-keys % [:user/whoami])}
                  :started-callback
                  (fn [app]
                    (df/load app :articles/all preview/ArticlePreview)
                    (df/load app :tags/all home/Tag))
                  :networking {:remote (net/fulcro-http-remote
                                         {:url "/api"
                                          :response-middleware (net/wrap-fulcro-response wrap-remember-token)
                                          :request-middleware  (net/wrap-fulcro-request wrap-with-token)})}}})
(dc/start-devcard-ui!)

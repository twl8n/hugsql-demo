(defproject hugsql-demo "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.layerware/hugsql "0.4.8"] ;; use with clojure.java.jdbc
                 [com.layerware/hugsql-core "0.4.8"] ;; use with clojure.jdbc
                 [com.layerware/hugsql-adapter-clojure-jdbc "0.4.8"] ;; use with clojure.jdbc
                 ;; https://github.com/funcool/clojure.jdbc
                 [funcool/clojure.jdbc "0.9.0"] ;; use with hugsql-core and hugsql-adapter-clojure-jdbc
                 [org.clojure/java.jdbc "0.7.5"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.xerial/sqlite-jdbc "3.21.0"]
                 [org.postgresql/postgresql "42.1.4"]
                 [org.postgresql/postgresql "9.4.1212"]]

  ;; The tutorial may be out of date. the :uberjar profile probably obviates :main and :aot.
  ;; https://github.com/technomancy/leiningen/blob/master/doc/TUTORIAL.md#uberjar
  ;; :main my-stuff.core
  ;; :aot [my-stuff.core]

  :main ^:skip-aot hugsql-demo.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

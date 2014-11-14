(defproject elm "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [org.clojure/clojurescript "0.0-2277"]
                 [om "0.7.0"]]
  :plugins [[com.keminglabs/cljx "0.4.0"]
            [lein-cljsbuild "1.0.3"]]

  :cljx {:builds [{:source-paths ["src/cljx"]
                   :output-path "target/generated/src/clj"
                   :rules :clj}

                  {:source-paths ["src/cljx"]
                   :output-path "target/generated/src/cljs"
                   :rules :cljs}]}

  :hooks [cljx.hooks]

  :source-paths ["target/generated/src/clj" "src/clj"]
  :jar-exclusions [#"\.cljx|\.swp|\.swo|\.DS_Store"]
  :cljsbuild {:builds
              {:mario {:source-paths ["src/clj" "target/generated/src/cljs" "src/cljs"]
                       :compiler {:preamble ["react/react.min.js"]
                                  :externs ["react/externs/react.js"]
                                  :output-to "target/mario.js"
                                  :optimizations :advanced
                                  :pretty-print false}}}})

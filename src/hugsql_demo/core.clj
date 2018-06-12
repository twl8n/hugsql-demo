(ns hugsql-demo.core
  (:require [clojure.tools.namespace.repl :as tnr]
            [clojure.repl :refer [doc]]
            [clojure.java.shell]
            [clojure.java.io]
            [jdbc.core :as jdbc] ;; clojure.jdbc
            [clojure.java.jdbc]
            [clojure.string :as str]
            [hugsql.core :as hugsql]
            [hugsql.adapter.clojure-jdbc :as cj-adapter])
  (:gen-class))

(defn pw-read []
  "Read the password file and return the password. Return an empty string on any error."
  (str/trim
   (try (slurp ".dbpass.txt")
        (catch Exception e (do 
                             (.printStackTrace e)
                             "")))))

(def dbspec-sqlite
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "hdemo.db"
   })

;; https://github.com/clojure/java.jdbc
;; This is not correct, in spite of what the docs seem to say
(def dbspec
  {:dbtype "postgresql"
   :classname   "org.postgresql.Driver"
   :subprotocol "postgresql"
   :dbname "hdemo" ;; db name
   :subname "//localhost:5432/hdemo"
   :user "dbuser"
   :password (pw-read)
   :port 5432
   :hostname "127.0.0.1"})

;; Only if you have enabled ssl on your postgres server.
;; :ssl true
;; :sslfactory "org.postgresql.ssl.NonValidatingFactory"

;; https://github.com/clojure-cookbook/clojure-cookbook/blob/master/06_databases/6-01_connecting-to-an-SQL-database.asciidoc
;; This may only work with the old, previous generation postgres driver [postgresql/postgresql "9.3-1102.jdbc41"]
(def dbspec-java-jdbc
  {:classname   "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname     "hdemo"
   :user "dbuser"
   :password (pw-read)})

;; Hugsql macros must be outside defn and come before any mention of functions that they will create at
;; runtime. Two functions will be created for each :name in full.sql. 

(hugsql/def-db-fns "hugsql_demo/full.sql"
  {:adapter (cj-adapter/hugsql-adapter-clojure-jdbc)})

(hugsql/def-sqlvec-fns "hugsql_demo/full.sql"
  {:adapter (cj-adapter/hugsql-adapter-clojure-jdbc)})


;; 2570 ms/10K records, essentially the same as the far simpler (demo-transaction) below.
(defn demo-java-jdbc-t2
  "Prepare a statement with placeholder params, and run inside a transaction."
  []
  (let [conn (clojure.java.jdbc/get-connection dbspec-java-jdbc)
        stmt (clojure.java.jdbc/prepare-statement conn "insert into address (street, city, postal_code) values (?, ?, ?)")]
    (clojure.java.jdbc/with-db-transaction [tx {:connection conn}]
      (doseq [xx (range 10000)]
        (clojure.java.jdbc/execute!
         tx [stmt "Bayview" (str xx " Maple Street") "93312"])))))


;; 3100 ms/10K records. Roughtly 30% slower than clojure.jdbc, and slower than a non-prepared statement.
(defn demo-java-jdbc-t1
  "Run inserts inside a transaction. Sometimes a connection is a {:connection conn}. I can't decipher the
  difference in the docs."
  []
  (let [conn {:connection (clojure.java.jdbc/get-connection dbspec-java-jdbc)}]
    (clojure.java.jdbc/with-db-transaction [tx conn]
      (doseq [xx (range 10000)]
        (clojure.java.jdbc/execute!
         tx
         (insert-address-sqlvec {:city "Bayview" :street (str xx " Maple Street") :postal_code "93312"}))))))


;; The HugSQL docs point out that tuple list param only works for insert, it depends support from the
;; underlying db, and there is a limit to the size of the param vector. I would add that if you want raw
;; insert speed, you need to be using the COPY command. Still, this is cool.

;; 650 ms/10K
;; That is 3x better than transactions, 6x better than auto-commiting each insert as a separate transaction.
;; I need another demo that uses a prepared statement, which will should shave off a little more time.
(defn demo-vector
  [dbs]
  (let [conn (jdbc/connection dbs)]
    (insert-address-vector conn
                           {:address
                            (mapv (fn [xx] ["Bayview" (str xx " Maple Street") "93312"])
                                   (range 10000))})))


(defn demo-prep
  "This does not work. Prepared statements are not supported by clojure.jdbc, except that they are used
  internally for fetch-lazy using a cursor. Postgres supports prepared statements. Older code (Perl DBI)
  supports prepare for all relevant query types, so this feature is hardly new. I need to check support in
  clojure.java.jdbc."
  []
  (let [conn (jdbc/connection dbspec)]  
    (jdbc/atomic conn
                 (let [stmt (jdbc/prepared-statement conn ["insert into address (street, city, postal_code)\nvalues (?, ?, ?)"])]
                   (doseq [xx (range 10000)]
                     (jdbc/execute stmt [(str xx " Maple Street") "Bayview" "93312"]))))))
                                   
    
;; 210 ms/1k
;; 1970 ms/10k
;; After adding an index: 2250 md/10k
(defn demo-transaction
  []
  (let [conn (jdbc/connection dbspec)]
    (jdbc/atomic conn 
                 (doseq [xx (range 10000)]
                   (insert-address conn {:city "Bayview" :street (str xx " Maple Street") :postal_code "93312"})))))


;; 450 ms/1k
;; 4400 ms/10K
;; After adding an index: 4780 ms/10k
(defn demo-non-transaction
  []
  (let [conn (jdbc/connection dbspec)]
    (doseq [xx (range 10000)]
      (insert-address conn {:city "Bayview" :street (str xx " Maple Street") :postal_code "93312"}))))
  

(defn demo
  "Using clojure.jdbc. Run some code and def vars that you can inspect in the repl."
  []
  (def conn (jdbc/connection dbspec))
  (def check-res (jdbc/fetch conn ["SELECT 1"]))
  (jdbc/execute conn ["insert into address (city) values ('foo')"])
  (def in-res (insert-address conn {:city "Bayview" :street "110 Maple Street" :postal_code "93312"}))
  (def foo-res (address-ilike-city conn {:city "foo"}))
  (def bv-res (address-ilike-city conn {:city "bayview"})))

(defn -main
  "Using clojure.jdbc"
  [& args]
  (def conn (jdbc/connection dbspec))
  (def results (jdbc/fetch conn ["SELECT 1"])) ;; Verify the connection to the db.
  (jdbc/execute conn ["insert into address (city) values ('foo')"])
  (def aselect (address-ilike-city conn {:city "foo"})))


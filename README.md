#### todo

- move all the db config into local external files not checked into the repo, which are read once when
connecting to the db.

- find out file names which git will always ignore because they (nearly) always contain sensitive info

#### hugsql-demo

Install leiningen. Clone this repo. 

You'll need Postgres or SQLite. If you're running Postgres, the db server must be started. Modify the dbspec
in core.clj for your database.

Setting a Postgres password will no have any effect until you change local connections in pg_hba.conf from
"trust" to "md5". Postgres trusted connections do not require a password, but are only safe for connections on
the localhost (and trust is only valid for localhost).

Run your database command line, and load the appropriate schema file. 

For postgres it might be simpler to use the command line `createuser` and `createdb`, but the schema file seems to work.

```
> psql postgres
postgres=# \i resources/schema.sql
postgres=# \q
> 


> sqlite3 hdemo.db
sqlite> .read resources/schema_sqlite.sql
sqlite> .q
>

```

Put your Postgres database password in the local file .dbpass.txt and do not check this file into version
control. (Yes the file name begins with a dot and is therefore called a "dotfile". Dot files are often
configuration, and often not checked into version control.)

```
> psql hdemo
postgres=# alter user dbuser with encrypted password 'foobar';
postgres=# \q
> echo "foobar" > .dbpass.txt
```


Launch the repl and run the demo functions from the repl. If you aren't running cider, you can run
(tnr/refresh) in the repl after making changes to the code, in order to force the repl to pick up the changes.

```
hugsql-demo.core=> (tnr/refresh)
:reloading (hugsql-demo.core hugsql-demo.core-test)
:ok
hugsql-demo.core=> (time (demo-vector dbspec-sqlite))
"Elapsed time: 152.925096 msecs"
10000
hugsql-demo.core=> (time (demo-vector dbspec-sqlite))
"Elapsed time: 164.23081 msecs"
10000
hugsql-demo.core=> (time (demo-vector dbspec))
"Elapsed time: 677.292442 msecs"
10000
hugsql-demo.core=> (time (demo-vector dbspec))
"Elapsed time: 626.836757 msecs"
10000
hugsql-demo.core=> 
```

#### Links to docs

http://funcool.github.io/clojure.jdbc/latest/

https://www.hugsql.org/



#### fetch and execute are not interchangeable

Anyone who knows me knows that I'm fond of Perl DBD. There's a long list of great features in DBD, many of
which are missing from other database connectivity libraries. I'm convinced that Tim Bunce is a genius. One
example is that DBD execute simply works on everything. This is not true in Clojure. However, if you are lucky
enough to be using Postgres and if you want to always use fetch, then "returning ..." is your friend.

Certainly, it doesn't take much thinking to know if you need fetch or execute. Still, why is the high level
programmer being bothered by this kind of trivia? If I want to waste the output of my mitochondria on trivia
I'll program in Go or C.

I haven't tried this in real life, but "returning id" works (or "returning 1" kind of, if only 1 row is
effected). For an accurate count of rows affected, one would have to write a plpgsql function, which is far
too much hassle.

https://stackoverflow.com/questions/38875955/postgresql-function-return-affected-row-count

```
hugsql-demo.core=> (def conn (jdbc/connection dbspec))
#'hugsql-demo.core/conn
hugsql-demo.core=> (jdbc/fetch conn ["select 1"])
[{:?column? 1}]
hugsql-demo.core=> (jdbc/execute conn ["select 1"])

PSQLException A result was returned when none was expected.  org.postgresql.jdbc.PgPreparedStatement.executeUpdate (PgPreparedStatement.java:141)
hugsql-demo.core=> (jdbc/fetch conn ["insert into address (city) values ('foo')"])

PSQLException No results were returned by the query.  org.postgresql.jdbc.PgPreparedStatement.executeQuery (PgPreparedStatement.java:118)
hugsql-demo.core=> (jdbc/fetch conn ["insert into address (city) values ('foo') returning id"])
[{:id 347018}]
hugsql-demo.core=> (jdbc/fetch conn ["insert into address (city) values ('foo') returning 1"])
[{:?column? 1}]
hugsql-demo.core=> (jdbc/fetch conn ["update address set city='bar' where city='foo' returning id;"])
[{:id 347017} {:id 347018} {:id 347019}]

```

Really, just use execute for insert, update, delete. Use fetch for select.



#### Prepared statements

Prepare is not supported by HugSQL, nor explicitly by clojure.jdbc (except for internal use in fetch-lazy). 

Clojure.java.jdbc has prepareed statements. See core.clj functions demo-java-jdbc-t1 and demo-java-jdbc-t2.

However:

- prepared statements are awkward for several reasons. prepare-statement takes a raw connection, but
  with-db-transaction, execute! and query all take a {:connection conn}

- clojure.jdbc is faster running repeated parameterized SQL inside a transaction than a clojure.java.jdbc
  prepared statement inside a transaction

- clojure.java.jdbc requires more lines of code


#### todo

* There's no error checking. Need to deal with that because silent SQL failures can be really irritating bugs.

* Might be a good idea to include working clojure.java.jdbc code.

* Might be nice to turn this into a real app. It won't ever do much, but right now everything interesting is
  limited to the repl, so this is hardly a completely working example.

* Need to test and/or demo cursors. Postgres has them, but we are better off using some level of abstraction.



#### HugSQL header overview

Shorthand and longhand versions of a HugSQL header in a sql file. See resources/full.sql.

The :command specifies what kind of action. The :result specifies the result data structure. We will call a
clojure.lang.PersistentArrayMap a "hash-map".

```
-- A comment. Assume id is a unique primary key.
-- :name foo :? :1
-- :doc Query the database returning a single record.
select * from foo where id = :id

-- A comment. Assume id is a unique primary key.
-- :name foo
-- :command :query
-- :result :one
-- :doc Query the database returning a single record.
select * from foo where id = :id

```

Available :command values:

- :query or :? = query with a result-set (default)
- :execute or :! = any statement
- :returning-execute or :<! = support for INSERT ... RETURNING
- :insert or :i! = support for insert and jdbc .getGeneratedKeys

:query and :execute mirror the distinction between query and execute! in the clojure.java.jdbc library and
fetch and execute in the clojure.jdbc library.


| command  | clojure.java.jdbc | clojure.jdbc |
|----------|-------------------|--------------|
| :query   | query             | fetch        |
| :execute | execute!          | execute      |


Available :result values:

- :one or :1 = one row, a hash-map
- :many or :* = many rows, a vector of hash-map
- :affected or :n = number of rows affected (inserted/updated/deleted), an integer
- :raw = passthrough an untouched result (default)

The default :result is :raw, which may be omitted. The default seems to be a vector of hash-map, but you
should verify this for your database connection library.



#### Odd errors you may encounter

If you leave the db connection arg off a SQL function, you will get a less-than-obvious error:

```
ExceptionInfo Parameter Mismatch: :street parameter data not found.  clojure.core/ex-info (core.clj:4617)
```

Incorrect hash-map of vector passed as :tuple*

```
PSQLException Can't infer the SQL type to use for an instance of clojure.lang.PersistentVector. Use setObject() with an explicit Types value to specify the type to use.  org.postgresql.jdbc.PgPreparedStatement.setObject (PgPreparedStatement.java:973)
```


This is caused by having a bad db-spec:

```
NoInitialContextException Need to specify class name in environment or system property, or as an applet parameter, or in an application resource file:  java.naming.factory.initial  javax.naming.spi.NamingManager.getInitialContext (NamingManager.java:662)
```

Correct docs here:

https://github.com/clojure-cookbook/clojure-cookbook/blob/master/06_databases/6-01_connecting-to-an-SQL-database.asciidoc

Wrong or out of date docs here:

http://peterstratton.com/posts-output/2017-01-28-postgres-and-clojure-using-clojure-java-jdbc/


```
(def dbspec-java-jdbc
  {:classname   "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname     "hdemo"
   :user "dbuser"
   :password (pw-read)})

(def dbspec-java-example
  {:classname "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname "//localhost:5432/hdemo" ;; is this //<host>:<port>/<dbname> ??
   :user "dbuser"
   :password "foobar"})

;; Wrong
(def bad-dbspec-java-jdbc
  {:dbtype "postgresql"
   :name "hdemo"
   :host "localhost"      ;; Optional
   :port 5432             ;; Optional
   :user "deuser"
   :password "foobar"
   :ssl false
   :sslfactory "org.postgresql.ssl.NonValidatingFactory"})
```

Fix this by changing the database to be owned by the user `alter database hdemo owner to dbuser;`

```
PSQLException ERROR: permission denied for relation address  org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse (QueryExecutorImpl.java:2455)
```


## License

Copyright © 2017 Tom Laudeman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

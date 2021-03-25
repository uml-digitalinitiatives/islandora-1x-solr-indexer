# islandora-1x-solr-indexer
## Information
This replaces the GSearch indexer with a simple camel route that could be extended easily.

## Java
**This requires Java 11 to build and run**

## Deployment
1. Clone the repository.
1. Change into the directory.
1. Run `./gradlew build install`.
1. Copy the `./build/libs/islandora-1x-solr-indexer.jar` to wherever you'd like.
1. Copy the `example.properties` file and edit as necessary.
1. Run JAR using the environment variable `fc3indexer.config.file` to point to your file.


## Configuration
Configuration is done via a properties file, copy and edit the [`example.properties`](example.properties) file
as needed.

Point to your customized properties file using the `fc3indexer.config.file` variable.

```shell
java -Dfc3indexer.config.file=/absolute/path/myproperties.properties -jar islandora-1x-solr-indexer.jar
```

You **MUST** configure the location of your XSLT directory in the `xslt.path` option.

This *xslt.path* directory should contain XSLT files named with the same name as the datastream ID they will process (ie. RELS-EXT.xslt, DC.xslt, etc)

You **MUST** have at least one xslt named **FOXML.xslt** to handle the object XML.

These stylesheets should not output the XML declaration as the resulting XML is re-combined. So please ensure you have a
```
<xsl:output method="xml" encoding="UTF-8" omit-xml-declaration="yes"/>
```
in each of your XSLTs.

Besides the `xslt.path` option the other options are:

```
jms.brokerUrl=tcp://127.0.0.1:61616
```

* The hostname and port of the JMS broker

```
jms.username=
jms.password=
```

* Username/Password (if required) to connect to the JMS broker

```
queue.incoming=ext-activemq:queue:fedora_update
```

* The queue to read incoming messages from. The _ext-activemq:_ part aligns with the JMS bean wired internally to have a single consumer.

```
queue.internal=activemq:queue:internalIndex
```

* The indexer reads messages off of the `queue.incoming` queue into an aggregator. It will collect all the messages that occur within 10 seconds (configurable with `completion.timeout`) of each other and only process the last one. That message is passed to this internal queue 
  which specifies _activemq:_ to use the JMS bean which is consumed by the number of consumers defined in `jms.processes`.

    When ingesting objects in Fedora you normally get a JMS message for each of object ingest, datastream modify, etc. This helps to reduce the redundant indexing.

```
solr.processes=1
```

* Solr update/delete messages are processed this many at a time. Don't overload your Solr box.

```
fcrepo.baseUri=http://localhost:8080
fcrepo.basePath=/fedora
fcrepo.authUser=fedoraAdmin
fcrepo.authPassword=
```

* These define where your Fedora URI and a username/password to allow us to get the datastreams to index.

```
solr.baseUrl=solr://localhost:8080/solr
```

* Address of the Solr instance, should start with `solr://` as we use the [Camel Solr component](http://camel.apache.org/solr.html)

```
completion.timeout=10000
```

* How long (in milliseconds) to wait for messages in the aggregator. Defaults to 10 seconds.

```
reindexer.port=9111
reindexer.path=/fedora3-solr-indexer
```

* On localhost at this port and path a reindexer GET endpoint will be located.

```shell
custom.character.file=
```

* A file of characters to alter when converting from plain text to XML. If the file exists
each line should have the form `<character to remove>:<character to replace with>`

## How it works
This indexer watches the activemq queue for Fedora update messages. When one arrives:

1. If the header *methodName* is **purgeObject** then the object is automatically deleted from Solr using the header *pid*.
2. If not, the FOXML is retrieved from Fedora and the property *info:fedora/fedora-system:def/model#state* is checked. If it is **Active** the object is indexed, otherwise it is deleted.

### Indexing process
The FOXML is split up into **foxml:datastream** and processed, if the mime-type is **text/xml**, **application/xml**, **application/rdf+xml**, **text/html** or **text/plain** the datastream content is retrieved from Fedora and transformed using a stylesheet of the same name (as the datastream ID plus `.xslt`) in the directory specified by the `xslt.path` configuration parameter.

If an appropriate XSLT file does **not** exist, that datastream is skipped. 

The datastream ID is available in the XSLT as a parameter called **DSID**, you can also get the PID with a parameter named **pid**. These &lt;xsl:param&gt; statements should be at the top level of your XSLTs.

The resulting field XML is concatenated together using the *ca.umanitoba.dam.islandora.fc3indexer.utils.StringConcatAggregator* wrapped with a &lt;update&gt;&lt;doc&gt; &lt;/doc&gt;&lt;update&gt; and pushed to Solr as an update.

## Reindexing

There is a REST endpoint started that allows for forcing a reindex of objects without touching them in Fedora. 

Its address is `http://localhost:<reindexer.port>/<reindexer.path>/reindex/{pid}` where `{pid}` is the PID of the object to reindex.

It only allows GET requests and responds with a 200 OK and places an item directly onto the `queue.internal`

## Logging/Debugging

If you are experiencing trouble getting your object indexed you can increase the debugging level to **TRACE** which will give you a tremendous amount of information during processing. It is **not** recommended to leave the logging at this level for production use.

By default the log level is set to `INFO` for the indexer and `WARN` for Camel and other processes (ActiveMQ, Xalan), you can modify the level for the Fedora 3 Indexer or components using the following system properties.

* `fc3indexer.log.indexer` = Fedora 3 Indexer
* `fc3indexer.log.camel` = Apache Camel
* `fc3indexer.log.activemq` = Apache ActiveMQ
* `fc3indexer.log.xml` = Xalan and Java.xml

For example to set the indexer to `TRACE` and Apache Camel to `DEBUG`
```shell
java -Dfc3indexer.log.indexer=TRACE -Dfc3indexer.log.camel=DEBUG -jar islandora-1x-solr-indexer.jar
```

## Credit
All credit to [acoburn](https://github.com/acoburn) for this is just an implementation of his camel route.

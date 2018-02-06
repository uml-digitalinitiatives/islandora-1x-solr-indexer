# islandora-1x-solr-indexer
## Information
This replaces the GSearch indexer with a simple camel route that could be extended easily.

This is also an OSGI bundle and feature you can deploy to a Karaf container.

## Deployment
1. Clone the repository.
1. Change into the directory.
1. Run `sudo ./gradlew build install`.
1. This creates a new `build` directory, the absolute path to this is your `${build_dir}`
1. Login to your Karaf container.
1. Add the new repository `repo-add file:${build_dir}/resources/main/features.xml`
1. Refresh the repositories (to ensure you added it correctly), `repo-refresh`
1. Install the solr-indexer `feature:install islandora-1x-solr-indexer`

## Configuration
Configuration is via the deployed file in `${KARAF_HOME}/etc/ca.umanitoba.dam.islandora.fc3indexer.cfg`

You **MUST** configure the location of your XSLT directory in the [`xslt.path` option](https://github.com/uml-digitalinitiatives/islandora-1x-solr-indexer/blob/osgi-package/src/main/cfg/ca.umanitoba.dam.islandora.fc3indexer.cfg#L9).

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
queue.incoming=activemq:queue:fedora_update
```

* The queue to read incoming messages from. The _activemq:_ part aligns with the [bean ID defined in the blueprint](https://github.com/uml-digitalinitiatives/islandora-1x-solr-indexer/blob/osgi-package/src/main/resources/OSGI-INF/blueprint/blueprint.xml#L64) and is mandatory unless you are wiring your own JMS connection.

```
queue.internal=activemq:queue:internalIndex
```

* The indexer reads messages off of the `queue.incoming` queue into an aggregator. It will collect all the messages that occur within 10 seconds of each other and only process the last one. That message is passed to this internal queue.

    When ingesting objects in Fedora you normally get a JMS message for each of object ingest, datastream modify, etc. This helps to reduce the redundant indexing.

```    
queue.dead-letter=activemq:topic:trash
```

* Messages that fail in the queue are retried a number of times (`error.maxRedeliveries`) and then sent to this queue/topic.

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

* These define where your Fedora is and a username/password to allow us to get the datastreams to index.

```
error.maxRedeliveries=3
```

* How many times to try processing a message if you receive an error, once exhausted the message is directed to `queue.dead-letter`.

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

## How it works
This indexer watches the activemq queue for Fedora update messages. When one arrives:

1. If the header *methodName* is **purgeObject** then the object is automatically deleted from Solr using the header *pid*.
2. If not, the FOXML is retrieved from Fedora and the property *info:fedora/fedora-system:def/model#state* is checked. If it is **Active** the object is indexed, otherwise it is deleted.

### Indexing process
The FOXML is split up into **foxml:datastream** and processed, if the mime-type is **text/xml**, **application/xml**, **application/rdf+xml** or **text/html** the datastream content is retrieved from Fedora and transformed using a stylesheet of the same name (as the datastream ID plus `.xslt`) in the directory specified by the `xslt.path` configuration parameter.

If an appropriate XSLT file does **not** exist, that datastream is skipped. 

The datastream ID is available in the XSLT as a parameter called **DSID**, you can also get the PID with a parameter named **pid**. These &lt;xsl:param&gt; statements should be at the top level of your XSLTs.

The resulting field XML is concatenated together using the *ca.umanitoba.dam.islandora.fc3indexer.StringConcatAggregator* wrapped with a &lt;update&gt;&lt;doc&gt; &lt;/doc&gt;&lt;update&gt; and pushed to Solr as an update.

## Reindexing

There is a REST endpoint started that allows for forcing a reindex of objects without touching them in Fedora. 

Its address is `http://localhost:<reindexer.port>/<reindexer.path>/reindex/{pid}` where `{pid}` is the PID of the object to reindex.

It only allows GET requests and responds with a 200 OK and places an item directly onto the `queue.internal`

## Debugging

If you are experiencing trouble getting your object indexed you can increase the debugging level to **TRACE** which will give you a tremendous amount of information during processing. It is **not** recommended to leave the logging at this level for production use.

## Credit
All credit to [acoburn](https://github.com/acoburn) for this is just an implementation of his camel route.
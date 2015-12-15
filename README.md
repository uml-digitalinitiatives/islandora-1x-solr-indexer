# fc3-camel-indexer
## Purpose
Replace the aging GSearch indexer with a simple camel route that could be extended easily.

## Deployment
1. Clone the repository.
1. Change into the directory.
1. Run ```mvn clean install```
1. Copy the fc3-camel-indexer.war file to your webapp container.
1. Configure as below.

## Configuration
Uses several overridable java options.

This option **MUST** be set:

* fedora3.indexer.xslt - The directory of the XSLTs to apply

The *fedora3.indexer.xslt* directory should contain XSLT files named with the same name as the datastream ID they will process (ie. RELS-EXT.xslt, DC.xslt, etc)

You must have at least one xslt named **FOXML.xslt** to handle the object XML.

You can specify the directory path with ```-Dfedora3.indexer.xslt=file:///full/path/to/xslt```

These stylesheets should not output the XML declaration as the resulting XML is re-combined. So please ensure you have a
```
<xsl:output method="xml" encoding="UTF-8" omit-xml-declaration="yes"/>
```

in each of your XSLTs.

The other configuration options are:

* fedora3.indexer.fedoraUrl - The URL of your Fedora instance (default: localhost:8080/)
* fedora3.indexer.fedoraPath - The base path at the above URL for fedora (default: /fedora)
* fedora3.indexer.fedoraUser User with API-M privileges to Fedora (default: fedoraAdmin)
* fedora3.indexer.fedoraPass - Password for above user (default: fedoraAdmin)
* fedora3.indexer.solrUrl - The URL of your Solr instance (default: localhost:8080/solr)
* fedora3.indexer.jmsBroker - The JMS Broker (default: tcp://localhost:61616)
* fedora3.indexer.jmsQueue - The JMS queue to watch (default: queue:fedora.apim.update)

These can also be overridden by including -D&lt;parameter&gt;=&lt;value&gt; in your JAVA\_OPTS system variable.

## How it works
This indexer watches the active-mq queue for Fedora update messages. When one arrives:

1. If the header *methodName* is **purgeObject** then the object is automatically deleted from Solr using the header *pid*.
2. If not, the FOXML is retrieved from Fedora and the property *info:fedora/fedora-system:def/model#state* is checked. If it is **Active** the object is indexed, otherwise it is deleted.

### Indexing process
The FOXML is split up into *foxml:datastream* and processed in parallel, if the mime-type is *text/xml*, *application/xml*, *application/rdf+xml* or *text/html* the datastream content is retrieved from Fedora and transformed using a stylesheet of the same name in the directory specified by the *fedora3.indexer.xslt* system property.

If an appropriate XSLT file does *not* exist, that datastream is skipped. 

The datastream ID is available in the XSLT as a parameter called *DSID*, you can also get the PID with a parameter named *pid*. These &lt;xsl:param&gt; statements should be at the top level of your XSLTs.

The resulting field XML is concatenated together using the *ca.umanitoba.fc3indexer.StringConcatAggregator* wrapped with a &lt;update&gt;&lt;doc&gt; &lt;/doc&gt;&lt;update&gt; and pushed to Solr as an update.

## Credit
All credit to [acoburn](https://github.com/acoburn) for this is just an implementation of his camel route wrapped in a war deployment.
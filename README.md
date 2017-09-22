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

* jms.brokerUrl
* jms.username
* jms.password
* queue.incoming
* queue.internal
* queue.dead-letter
* concurrent.processes
* fcrepo.baseUri
* fcrepo.basePath
* fcrepo.authUser
* fcrepo.authPassword
* error.maxRedeliveries
* solr.baseUrl
* completion.timeout
* reindexer.port=9999

All configuration options are documented inside the configuration file.

## How it works
This indexer watches the active-mq queue for Fedora update messages. When one arrives:

1. If the header *methodName* is **purgeObject** then the object is automatically deleted from Solr using the header *pid*.
2. If not, the FOXML is retrieved from Fedora and the property *info:fedora/fedora-system:def/model#state* is checked. If it is **Active** the object is indexed, otherwise it is deleted.

### Indexing process
The FOXML is split up into *foxml:datastream* and processed in parallel, if the mime-type is *text/xml*, *application/xml*, *application/rdf+xml* or *text/html* the datastream content is retrieved from Fedora and transformed using a stylesheet of the same name in the directory specified by the *fedora3.indexer.xslt* system property.

If an appropriate XSLT file does *not* exist, that datastream is skipped. 

The datastream ID is available in the XSLT as a parameter called *DSID*, you can also get the PID with a parameter named *pid*. These &lt;xsl:param&gt; statements should be at the top level of your XSLTs.

The resulting field XML is concatenated together using the *ca.umanitoba.dam.islandora.fc3indexer.StringConcatAggregator* wrapped with a &lt;update&gt;&lt;doc&gt; &lt;/doc&gt;&lt;update&gt; and pushed to Solr as an update.

## Credit
All credit to [acoburn](https://github.com/acoburn) for this is just an implementation of his camel route.
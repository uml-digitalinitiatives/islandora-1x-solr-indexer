<?xml version="1.0" encoding="UTF-8"?>
<!-- MODS properties -->
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:foxml="info:fedora/fedora-system:def/foxml#"
     exclude-result-prefixes="foxml">
   
   <xsl:output method="xml" indent="yes" encoding="UTF-8" omit-xml-declaration="yes"/>
   
   <xsl:template match="foxml:digitalObject">
    
     <xsl:apply-templates select="/foxml:datastream[/@ID = 'MODS']" />
  </xsl:template>
</xsl:stylesheet>

<?xml version="1.0" encoding="UTF-8"?>
<!-- FOXML properties -->
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:foxml="info:fedora/fedora-system:def/foxml#"
     exclude-result-prefixes="foxml">
   
   <xsl:output method="xml" indent="yes" encoding="UTF-8" omit-xml-declaration="yes"/>
   
   <xsl:param name="pid"/>
   <xsl:param name="FEDORAUSER"/>
   <xsl:param name="FEDORAPASS"/>
   <xsl:param name="FEDORAURL">localhost:8080</xsl:param>
   <xsl:param name="FEDORAPATH">/fedora</xsl:param>

   <xsl:variable name="PROT">http</xsl:variable>
     
   <xsl:template match="foxml:digitalObject">
     <field name="PID"><xsl:value-of select="$pid"/></field>
     <!-- objectProperty templates below -->
     <xsl:apply-templates select="foxml:objectProperties/child::*" mode="FOXML-properties"/>
       
     <xsl:for-each select="foxml:datastream">
      <field name="fedora_datastreams_ms">
          <xsl:value-of select="@ID"/>
      </field>
    </xsl:for-each>
  </xsl:template>
     
     
  <xsl:template match="foxml:property[substring-after(@NAME, '#')='createdDate' or substring-after(@NAME, '#')='lastModifiedDate']" mode="FOXML-properties">
    <xsl:param name="prefix">fgs_</xsl:param>
    <xsl:param name="suffix">_dt</xsl:param>
    <field>
      <xsl:attribute name="name">
        <xsl:value-of select="concat($prefix, substring-after(@NAME,'#'), $suffix)"/>
      </xsl:attribute>
      <xsl:value-of select="@VALUE"/>
    </field>
  </xsl:template>

  <!-- Index the fedora properties -->
  <xsl:template match="foxml:property" mode="FOXML-properties">
    <xsl:param name="prefix">fgs_</xsl:param>
    <xsl:param name="suffix">_s</xsl:param>
    <field>
      <xsl:attribute name="name">
        <xsl:value-of select="concat($prefix, substring-after(@NAME,'#'), $suffix)"/>
      </xsl:attribute>
      <xsl:value-of select="@VALUE"/>
    </field>
  </xsl:template>
  
</xsl:stylesheet>
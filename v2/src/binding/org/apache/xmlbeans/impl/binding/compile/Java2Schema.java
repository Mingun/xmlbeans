/*
* The Apache Software License, Version 1.1
*
*
* Copyright (c) 2003 The Apache Software Foundation.  All rights
* reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
*
* 1. Redistributions of source code must retain the above copyright
*    notice, this list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright
*    notice, this list of conditions and the following disclaimer in
*    the documentation and/or other materials provided with the
*    distribution.
*
* 3. The end-user documentation included with the redistribution,
*    if any, must include the following acknowledgment:
*       "This product includes software developed by the
*        Apache Software Foundation (http://www.apache.org/)."
*    Alternately, this acknowledgment may appear in the software itself,
*    if and wherever such third-party acknowledgments normally appear.
*
* 4. The names "Apache" and "Apache Software Foundation" must
*    not be used to endorse or promote products derived from this
*    software without prior written permission. For written
*    permission, please contact apache@apache.org.
*
* 5. Products derived from this software may not be called "Apache
*    XMLBeans", nor may "Apache" appear in their name, without prior
*    written permission of the Apache Software Foundation.
*
* THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
* OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
* ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
* USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
* OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
* SUCH DAMAGE.
* ====================================================================
*
* This software consists of voluntary contributions made by many
* individuals on behalf of the Apache Software Foundation and was
* originally based on software copyright (c) 2003 BEA Systems
* Inc., <http://www.bea.com/>. For more information on the Apache Software
* Foundation, please see <http://www.apache.org/>.
*/
package org.apache.xmlbeans.impl.binding.compile;

import org.apache.xmlbeans.impl.binding.bts.*;
import org.apache.xmlbeans.impl.jam.*;
import org.w3.x2001.xmlSchema.*;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 * Transforms a set of JClasses into a BTS and a schema.  This is really just
 * a sketch at this point.
 *
 * @author Patrick Calahan <pcal@bea.com>
 */
public class Java2Schema {

  // =========================================================================
  // Constants

  private static final String JAVA_URI_SCHEME      = "java:";
  private static final String JAVA_NAMESPACE_URI   = "language_builtins";
  private static final String JAVA_PACKAGE_PREFIX  = "java.";

  private static final String TAG_CT               = "xsdgen:complexType";
  private static final String TAG_CT_TYPENAME      = TAG_CT+".typeName";
  private static final String TAG_CT_TARGETNS      = TAG_CT+".targetNamespace";
  private static final String TAG_CT_ROOT          = TAG_CT+".rootElement";

  private static final String TAG_EL               = "xsdgen:element";
  private static final String TAG_EL_NAME          = TAG_EL+".name";
  private static final String TAG_EL_NILLABLE      = TAG_EL+".nillable";

  private static final String TAG_AT               = "xsdgen:attribute";
  private static final String TAG_AT_NAME          = TAG_AT+".name";


  // =========================================================================
  // Variables

  private BindingFile mBindingFile;      //KILLME
  private BindingLoader mLoader;         //KILLME
  private SchemaDocument mSchemaDocument;//KILLME
  private SchemaDocument.Schema mSchema; //KILLME
  //
  private JavaToSchemaInput mInput;

  // =========================================================================
  // Constructors

  public Java2Schema(JavaToSchemaInput jtsi) {
    if (jtsi == null) {
      throw new IllegalArgumentException("null JavaToSchemaInput");
    }
    mInput = jtsi;
  }

  // =========================================================================
  // Public methods

  /**
   * Does the binding work and returns the result.
   */
  public JavaToSchemaResult bind() {
    final JavaToSchemaResultImpl out = new JavaToSchemaResultImpl(mInput);
    bind(mInput.getJClasses(),out);
    return new JavaToSchemaResult() {
      public BindingFileGenerator getBindingFileGenerator() { return out; }
      public SchemaGenerator getSchemaGenerator() { return out; }
      public JavaToSchemaInput getJavaSourceSet() { return out.getJavaSourceSet(); }
    };
  }

  // ========================================================================
  // Private methods

  /**
   *
   */
  private void bind(JClass[] classes, JavaToSchemaResultImpl tb) {
    tb.addBindingFile(mBindingFile = new BindingFile());
    mLoader = PathBindingLoader.forPath
            (new BindingLoader[] {mBindingFile,
                                  BuiltinBindingLoader.getInstance()});
    mSchemaDocument = SchemaDocument.Factory.newInstance();
    mSchema = mSchemaDocument.addNewSchema();
    for(int i=0; i<classes.length; i++) getBindingTypeFor(classes[i]);
    tb.addSchema(mSchemaDocument);
  }

  // =========================================================================
  // Private methods

  private static JavaName getJavaName(JClass jc) {
    return JavaName.forString(jc.getQualifiedName());
  }

  private BindingType getBindingTypeFor(JClass clazz) {
    BindingType out = mLoader.getBindingType(mLoader.lookupTypeFor(getJavaName(clazz)));
    if (out == null) {
      out = createBindingTypeFor(clazz);
    }
    return out;
  }

  private BindingType createBindingTypeFor(JClass clazz) {
    if (clazz.isPrimitive()) {
      throw new IllegalStateException(clazz.getSimpleName());
    }
    // create the schema type
    TopLevelComplexType xsdType = mSchema.addNewComplexType();
    String tns = getTargetNamespace(clazz);
    String xsdName = getAnnotation(clazz,TAG_CT_TYPENAME,clazz.getSimpleName());
    QName qname = new QName(tns,xsdName);
    xsdType.setName(xsdName);
    // create a binding type
    BindingTypeName btname = BindingTypeName.forPair(getJavaName(clazz),
                                                     XmlName.forTypeNamed(qname));
    ByNameBean bindType = new ByNameBean(btname);
    mBindingFile.addBindingType(bindType,true,true);
    String rootName = getAnnotation(clazz,TAG_CT_ROOT,null);
    if (rootName != null) {
      QName rootQName = new QName(tns, rootName);
      BindingTypeName docBtName = BindingTypeName.forPair(getJavaName(clazz), XmlName.forGlobalName(XmlName.ELEMENT, rootQName));
      SimpleDocumentBinding sdb = new SimpleDocumentBinding(docBtName);
      sdb.setTypeOfElement(btname.getXmlName());
      mBindingFile.addBindingType(sdb,true,true);
    }
    // run through the class' properties to populate the binding and xsdtypes
    //FIXME this is going to have to change to take inheritance into account
    JProperty props[] = clazz.getProperties();
    Group xsdSequence = null;
    List attributes = null;
    for(int i=0; i<props.length; i++) {
      if (props[i].getGetter() == null || props[i].getSetter() == null) {
        continue; // we can only deal with read-write props
      }
      boolean isAttribute = false;
      String propName = getAnnotation(props[i],TAG_AT_NAME,null);
      if (propName != null) {
        isAttribute = true;
      } else {
        propName = getAnnotation(props[i],TAG_EL_NAME,props[i].getSimpleName());
      }
      BindingType propType = getBindingTypeFor(props[i].getType());
      QNameProperty qprop = new QNameProperty();
      qprop.setBindingType(propType);
      qprop.setQName(new QName(tns,propName));
      qprop.setGetterName(props[i].getGetter().getSimpleName());
      qprop.setSetterName(props[i].getSetter().getSimpleName());
      {
        //nillable tag
        JAnnotation a = props[i].getAnnotation(TAG_EL_NILLABLE);
        if (a != null) {
          // if the tag is there but empty, set it to true.  is that weird?
          if (a.getStringValue().trim().length() == 0) {
            qprop.setNillable(true);
          } else {
            qprop.setNillable(a.getBooleanValue());
          }
        }
      }
      bindType.addProperty(qprop);
      // also populate the schema type
      if (!isAttribute) {
        if (xsdSequence == null) xsdSequence = xsdType.addNewSequence();
        LocalElement xsdElement = xsdSequence.addNewElement();
        xsdElement.setName(propName);
        xsdElement.setType(getBuiltinTypeNameFor(props[i].getType()));
      } else {
        Attribute xsdAtt = Attribute.Factory.newInstance();
        qprop.setAttribute(true);
        xsdAtt.setName(propName);
        xsdAtt.setType(getBuiltinTypeNameFor(props[i].getType()));
        if (attributes == null) attributes = new ArrayList();
        attributes.add(xsdAtt);
      }
    }
    // No add the attributes that we saved in the list to the xsdType.
    // This is a hack around an xbeans bug in which the sequences and
    // attributes are output in the order in which they were added (the
    // schema for schemas says the attributes always have to go last).
    if (attributes != null) {
      Attribute[] atts = new Attribute[attributes.size()];
      attributes.toArray(atts);
      xsdType.setAttributeArray(atts);
    }
    // check to see if they want to create a root elements from this type
    JAnnotation[] anns = clazz.getAnnotations(TAG_CT_ROOT);
    for(int i=0; i<anns.length; i++) {
      TopLevelElement root = mSchema.addNewElement();
      root.setName(anns[i].getStringValue());
      root.setType(qname);
      // FIXME still not entirely clear to me what we should do about
      // the binding file here
    }
    return bindType;
  }


  private void addAttributeFor(QNameProperty prop) {

  }

  //REVIEW seems like having this functionality in jam (getters w/defaults)
  //would be a good thing to add to JAM
  private static String getAnnotation(JElement elem, String annName, String dflt) {
    JAnnotation ann = elem.getAnnotation(annName);
    return (ann == null) ? dflt : ann.getStringValue();
  }

  //REVIEW seems like having this functionality in jam (getters w/defaults)
  //would be a good thing to add to JAM
  private static boolean getAnnotation(JElement elem, String annName, boolean dflt) {
    JAnnotation ann = elem.getAnnotation(annName);
    return (ann == null) ? dflt : ann.getBooleanValue();
  }

  private QName getBuiltinTypeNameFor(JClass clazz) {
    BindingType bt =
            mLoader.getBindingType(mLoader.lookupTypeFor(JavaName.forString(clazz.getQualifiedName())));
    if (bt != null) return bt.getName().getXmlName().getQName();
    System.out.println("no type found for "+clazz.getQualifiedName());
    return null; //FIXME
  }

  private String getTargetNamespace(JClass clazz) {
    JAnnotation ann = clazz.getAnnotation(TAG_CT_TARGETNS);
    if (ann != null) return ann.getStringValue();
    // Ok, they didn't specify it in the markup, so we have to
    // synthesize it from the classname.
    String pkg_name;
    if (clazz.isPrimitive()) {
      pkg_name = JAVA_NAMESPACE_URI;
    } else {
      JPackage pkg = clazz.getContainingPackage();
      pkg_name = (pkg == null) ? "" : pkg.getQualifiedName();
      if (pkg_name.startsWith(JAVA_PACKAGE_PREFIX)) {
        pkg_name = JAVA_NAMESPACE_URI+"."+
                pkg_name.substring(JAVA_PACKAGE_PREFIX.length());
      }
    }
    return JAVA_URI_SCHEME + pkg_name;
  }

  /*

  private static boolean isXmlObj(JClass clazz) {
    try {
      JClass xmlObj = clazz.forName("org.apache.xmlbeans.XmlObject");
      return xmlObj.isAssignableFrom(clazz);
    } catch(Exception e) {
      e.printStackTrace(); //FIXME
      return false;
    }
  }
  */
}
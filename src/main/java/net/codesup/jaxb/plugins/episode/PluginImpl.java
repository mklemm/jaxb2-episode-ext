/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2014 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package net.codesup.jaxb.plugins.episode;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.fmt.JPropertyFile;
import com.sun.codemodel.fmt.JTextFile;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.EnumOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.outline.PackageOutline;
import com.sun.tools.xjc.reader.Const;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BISchemaBinding;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BindInfo;
import com.sun.xml.bind.v2.schemagen.episode.Bindings;
import com.sun.xml.bind.v2.schemagen.episode.SchemaBindings;
import com.sun.xml.txw2.TXW;
import com.sun.xml.txw2.output.StreamSerializer;
import com.sun.xml.xsom.XSAnnotation;
import com.sun.xml.xsom.XSAttGroupDecl;
import com.sun.xml.xsom.XSAttributeDecl;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.XSContentType;
import com.sun.xml.xsom.XSDeclaration;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSIdentityConstraint;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSModelGroupDecl;
import com.sun.xml.xsom.XSNotation;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.XSWildcard;
import com.sun.xml.xsom.XSXPath;
import com.sun.xml.xsom.visitor.XSFunction;

/**
 * Creates the episode file,
 *
 * @author Kohsuke Kawaguchi
 * @author Ben Tomasini (ben.tomasini@gmail.com)
 * @author Mirko Klemm
 */
public class PluginImpl extends Plugin {
	public static final String XML_NS = "http://www.w3.org/XML/1998/namespace";

	public static final String SRC_MAIN_RESOURCES = "src/main/resources/";
	public static final String KSCS_BINDINGS_NS = "http://www.kscs.com/util/jaxb/bindings";
	private String episodePackageName = "META-INF";
	private String packageMappingPackageName = "META-INF";
	private String catalogPackageName = "";
	private String episodeFileName = "sun-jaxb.episode";
	private String packageMappingFileName = "jaxb-packages.properties";
	private String catalogFileName = "default.catalog";

	public String getOptionName() {
		return "Xepisode-ext";
	}

	public String getUsage() {
		return "  -Xepisode-ext    :  generate the episode file for separate compilation.";
	}

	public int parseArgument(final Options opt, final String[] args, int i) throws BadCommandLineException, IOException {
		return 0;
	}

	/**
	 * Capture all the generated classes from global schema components
	 * and generate them in an episode file.
	 */
	public boolean run(final Outline model, final Options opt, final ErrorHandler errorHandler) throws SAXException {
		// reorganize qualifying components by their namespaces to
		// generate the list nicely
		final Map<XSSchema, PerSchemaOutlineAdaptors> perSchema = new LinkedHashMap<>();
		boolean hasComponentInNoNamespace = false;

		// Combine classes and enums into a single list
		final List<OutlineAdaptor> outlines = new ArrayList<>();

		for (final ClassOutline co : model.getClasses()) {
			final XSComponent sc = co.target.getSchemaComponent();
			final String fullName = co.implClass.fullName();
			final String packageName = co.implClass.getPackage().name();
			final OutlineAdaptor adaptor;
//			if(isInterface(co)) {
//				final XSComponent schemaComponent = findGroupDeclaration(model, (XSDeclaration)sc);
//				adaptor = new OutlineAdaptor(schemaComponent, OutlineAdaptor.OutlineType.INTERFACE, fullName, packageName);
//			} else {
				adaptor = new OutlineAdaptor(sc, OutlineAdaptor.OutlineType.CLASS, fullName, packageName);
//			}
			outlines.add(adaptor);
		}

		for (final EnumOutline eo : model.getEnums()) {
			final XSComponent sc = eo.target.getSchemaComponent();
			final String fullName = eo.clazz.fullName();
			final String packageName = eo.clazz.getPackage().name();
			final OutlineAdaptor adaptor = new OutlineAdaptor(sc,
					OutlineAdaptor.OutlineType.ENUM, fullName, packageName);
			outlines.add(adaptor);
		}

		for (final OutlineAdaptor oa : outlines) {
			final XSComponent sc = oa.schemaComponent;

			if (sc != null && (sc instanceof XSDeclaration)) {
				final XSDeclaration decl = (XSDeclaration) sc;
				if (!decl.isLocal()) { // local components cannot be referenced from outside, so no need to list.
					PerSchemaOutlineAdaptors list = perSchema.get(decl.getOwnerSchema());
					if (list == null) {
						list = new PerSchemaOutlineAdaptors();
						perSchema.put(decl.getOwnerSchema(), list);
					}

					list.add(oa);

					if (decl.getTargetNamespace().equals(""))
						hasComponentInNoNamespace = true;
				}
			}
		}

		//final File episodeFile = new File(opt.targetDir, this.episodePackageName.replaceAll("\\.", "/") + "/" + this.episodeFileName);
		try (final StringWriter stringWriter = new StringWriter()) {
			final Bindings bindings = TXW.create(Bindings.class, new StreamSerializer(stringWriter));
			if (hasComponentInNoNamespace) // otherwise jaxb binding NS should be the default namespace
				bindings._namespace(Const.JAXB_NSURI, "jaxb");
			else
				bindings._namespace(Const.JAXB_NSURI, "");
//			bindings._namespace(PluginImpl.KSCS_BINDINGS_NS, "kscs");
			bindings.version("2.1");
			bindings._comment("\n\n" + opt.getPrologComment() + "\n  ");

			// generate listing per schema
			for (final Map.Entry<XSSchema, PerSchemaOutlineAdaptors> e : perSchema.entrySet()) {
				final PerSchemaOutlineAdaptors ps = e.getValue();
				final Bindings group = bindings.bindings();
				final String tns = e.getKey().getTargetNamespace();
				if(PluginImpl.XML_NS.equals(tns)) {
					group._namespace(tns, "xml");
				} else if (!tns.equals("")) {
					group._namespace(tns, "tns");
				}

				group.scd("x-schema::" + (tns.equals("") ? "" : (PluginImpl.XML_NS.equals(tns) ? "xml" : "tns")));
				group._attribute("if-exists", "true");

				final SchemaBindings schemaBindings = group.schemaBindings();
				schemaBindings.map(false);
				if (ps.packageNames.size() == 1) {
					final String packageName = ps.packageNames.iterator().next();
					if (packageName != null && packageName.length() > 0) {
						schemaBindings._package().name(packageName);
					}
				}

				for (final OutlineAdaptor oa : ps.outlineAdaptors) {
					final Bindings child = group.bindings();
					oa.buildBindings(child);
				}
				group.commit(true);
			}

			bindings.commit();

			final JTextFile jTextFile = new JTextFile(this.episodeFileName);
			jTextFile.setContents(stringWriter.toString());
			model.getModel().codeModel._package(this.episodePackageName).addResourceFile(jTextFile);
		} catch (final IOException e) {
			errorHandler.error(new SAXParseException("Failed to write to " + this.episodeFileName, null, e));
			return false;
		}

		generatePackageMapping(model);

		generateCatalog(model, opt, outlines);
		return true;
	}

	private boolean isInterface(final ClassOutline co) {
		final JCodeModel codeModel = co.parent().getCodeModel();
		final JDefinedClass foundClass = codeModel._getClass(co.implClass.fullName());
		return foundClass != null && foundClass.isInterface();
	}

	private XSDeclaration findGroupDeclaration(final Outline model, final XSComponent derivedComponent) {
		final XSDeclaration decl = (XSDeclaration)derivedComponent;
		final XSAttGroupDecl attGroupDecl = model.getModel().schemaComponent.getAttGroupDecl(decl.getTargetNamespace(), decl.getName());
		return attGroupDecl == null ? model.getModel().schemaComponent.getModelGroupDecl(decl.getTargetNamespace(), decl.getName()) : attGroupDecl;
	}

	private void generatePackageMapping(final Outline model) {
		final JPropertyFile mappingFile = new JPropertyFile(this.packageMappingFileName);

		for(final PackageOutline packageOutline : model.getAllPackageContexts()) {

			final String name = packageOutline._package().name();
			final String namespaceURI = packageOutline.getMostUsedNamespaceURI();
			mappingFile.add(name, namespaceURI == null ? "" : namespaceURI);
		}

		model.getCodeModel()._package(this.packageMappingPackageName).addResourceFile(mappingFile);
	}

	private void generateCatalog(final Outline model, final Options opt, final List<OutlineAdaptor> publicSchemComponents) {
		final JTextFile catalogFile = new JTextFile(this.catalogFileName);

		final Map<String,String> systemIds = new HashMap<>();
		for(final OutlineAdaptor outlineAdaptor:publicSchemComponents) {
			systemIds.put(outlineAdaptor.schemaComponent.getSourceDocument().getTargetNamespace(), outlineAdaptor.schemaComponent.getSourceDocument().getSystemId());
		}

		final StringBuilder stringBuilder = new StringBuilder();
		for(final Map.Entry<String,String> entry : systemIds.entrySet()) {
			final String systemId = entry.getValue();
			final int pos = systemId.indexOf(PluginImpl.SRC_MAIN_RESOURCES);
			if(pos >= 0) {
				final String relativePath = systemId.substring(pos + PluginImpl.SRC_MAIN_RESOURCES.length());
				stringBuilder.append("PUBLIC \"");
				stringBuilder.append(entry.getKey());
				stringBuilder.append("\" \"");
				stringBuilder.append(relativePath);
				stringBuilder.append("\"\n");
			}
		}
		catalogFile.setContents(stringBuilder.toString());
		model.getCodeModel()._package(this.catalogPackageName).addResourceFile(catalogFile);
	}

	/**
	 * Computes SCD.
	 * This is fairly limited as JAXB can only map a certain kind of components to classes.
	 */
	private static final XSFunction<String> SCD = new XSFunction<String>() {
		private String name(final XSDeclaration decl) {
			if (decl.getTargetNamespace().equals("")) {
				return decl.getName();
			} else if(PluginImpl.XML_NS.equals(decl.getTargetNamespace())) {
				return "xml:" + decl.getName();
			} else {
				return "tns:" + decl.getName();
			}
		}

		public String complexType(final XSComplexType type) {
			return "~" + name(type);
		}

		public String simpleType(final XSSimpleType simpleType) {
			return "~" + name(simpleType);
		}

		public String elementDecl(final XSElementDecl decl) {
			return name(decl);
		}

		// the rest is doing nothing
		public String annotation(final XSAnnotation ann) {
			throw new UnsupportedOperationException();
		}

		public String attGroupDecl(final XSAttGroupDecl decl) {
			return "attributeGroup::"+name(decl);
		}

		public String attributeDecl(final XSAttributeDecl decl) {
			throw new UnsupportedOperationException();
		}

		public String attributeUse(final XSAttributeUse use) {
			throw new UnsupportedOperationException();
		}

		public String schema(final XSSchema schema) {
			throw new UnsupportedOperationException();
		}

		public String facet(final XSFacet facet) {
			throw new UnsupportedOperationException();
		}

		public String notation(final XSNotation notation) {
			throw new UnsupportedOperationException();
		}

		public String identityConstraint(final XSIdentityConstraint decl) {
			throw new UnsupportedOperationException();
		}

		public String xpath(final XSXPath xpath) {
			throw new UnsupportedOperationException();
		}

		public String particle(final XSParticle particle) {
			throw new UnsupportedOperationException();
		}

		public String empty(final XSContentType empty) {
			throw new UnsupportedOperationException();
		}

		public String wildcard(final XSWildcard wc) {
			throw new UnsupportedOperationException();
		}

		public String modelGroupDecl(final XSModelGroupDecl decl) {
			return "group::"+name(decl);
		}

		public String modelGroup(final XSModelGroup group) {
			throw new UnsupportedOperationException();
		}
	};

	private final static class OutlineAdaptor {

		private enum OutlineType {

			CLASS(new BindingsBuilder() {
				public void build(final OutlineAdaptor adaptor, final Bindings bindings) {
					bindings.klass().ref(adaptor.implName);
				}
			}),
			ENUM(new BindingsBuilder() {
				public void build(final OutlineAdaptor adaptor, final Bindings bindings) {
					bindings.typesafeEnumClass().ref(adaptor.implName);
				}
			}),
			INTERFACE(new BindingsBuilder() {
							public void build(final OutlineAdaptor adaptor, final Bindings bindings) {
								bindings._element(PluginImpl.KSCS_BINDINGS_NS, "interface", Interface.class).ref(adaptor.implName);
							}
						});

			private final BindingsBuilder bindingsBuilder;

			private OutlineType(final BindingsBuilder bindingsBuilder) {
				this.bindingsBuilder = bindingsBuilder;
			}

			private interface BindingsBuilder {
				void build(OutlineAdaptor adaptor, Bindings bindings);
			}

		}

		private final XSComponent schemaComponent;
		private final OutlineType outlineType;
		private final String implName;
		private final String packageName;

		public OutlineAdaptor(final XSComponent schemaComponent, final OutlineType outlineType,
		                      final String implName, final String packageName) {
			this.schemaComponent = schemaComponent;
			this.outlineType = outlineType;
			this.implName = implName;
			this.packageName = packageName;
		}

		private void buildBindings(final Bindings bindings) {
			bindings.scd(this.schemaComponent.apply(PluginImpl.SCD));
			this.outlineType.bindingsBuilder.build(this, bindings);
		}
	}

	private final static class PerSchemaOutlineAdaptors {

		private final List<OutlineAdaptor> outlineAdaptors = new ArrayList<>();

		private final Set<String> packageNames = new HashSet<>();

		private void add(final OutlineAdaptor outlineAdaptor) {
			this.outlineAdaptors.add(outlineAdaptor);
			this.packageNames.add(outlineAdaptor.packageName);
		}

	}

	private BISchemaBinding getSchemaBinding(final XSComponent xsComponent) {
		return xsComponent.getOwnerSchema().getAnnotation() != null ? ((BindInfo) xsComponent.getOwnerSchema().getAnnotation().getAnnotation()).get(BISchemaBinding.class) : null;
	}
}

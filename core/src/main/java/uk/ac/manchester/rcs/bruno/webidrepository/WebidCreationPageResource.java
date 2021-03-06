/*-----------------------------------------------------------------------
  
Copyright (c) 2007-2010, The University of Manchester, United Kingdom.
All rights reserved.

Redistribution and use in source and binary forms, with or without 
modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, 
      this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright 
      notice, this list of conditions and the following disclaimer in the 
      documentation and/or other materials provided with the distribution.
 * Neither the name of The University of Manchester nor the names of 
      its contributors may be used to endorse or promote products derived 
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
POSSIBILITY OF SUCH DAMAGE.

-----------------------------------------------------------------------*/
package uk.ac.manchester.rcs.bruno.webidrepository;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.rdfxml.RDFXMLWriter;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import uk.ac.manchester.rcs.corypha.core.CoryphaTemplateUtil;
import uk.ac.manchester.rcs.corypha.core.HibernateFilter;

/**
 * This is a resource class for the records.
 * 
 * @author Bruno Harbulot (Bruno.Harbulot@manchester.ac.uk)
 * 
 * @param <RecordT>
 *            type of record
 * @param <CoreDataT>
 *            type of core-data (XmlBeans)
 */
public class WebidCreationPageResource extends ServerResource {
    @SuppressWarnings("unused")
    private static final Log LOGGER = LogFactory
            .getLog(WebidCreationPageResource.class);

    /**
     * Extracts the resource ID from the request and loads the record.
     * 
     * @return true if such a record exists, false if it doesn't.
     */
    @Override
    public void doInit() {
        super.doInit();
        setNegotiated(true);
    }

    @Get("html")
    public Representation toHtml() {
        HashMap<String, Object> data = new HashMap<String, Object>();
        return CoryphaTemplateUtil.buildTemplateRepresentation(getContext(),
                getRequest(), "foafcreation.ftl.html", data,
                MediaType.TEXT_HTML);
    }

    @Post
    public Representation acceptRepresentation(Representation entity)
            throws ResourceException {
        try {
            Form form = new Form(entity);

            String familyName = form.getFirstValue("familyName");
            String givenName = form.getFirstValue("givenName");

            String localId = UUID.randomUUID().toString() + "/";

            Repository repository = (Repository) getContext().getAttributes()
                    .get(WebidModule.FOAFDIRECTORY_SESAME_REPOSITORY_ATTRIBUTE);
            RepositoryConnection conn = repository.getConnection();
            try {
                ValueFactory vf = conn.getValueFactory();
                URI context = vf.createURI(getRequest().getResourceRef()
                        + "profile/", localId);

                conn.clear(context);

                Resource subject = vf.createURI(context.toString(), "#me");

                URI predicate = RDF.TYPE;
                Value value = vf.createURI(WebidModule.FOAF_NS + "Person");
                conn.add(subject, predicate, value, context);

                if (givenName != null) {
                    predicate = vf.createURI(WebidModule.FOAF_NS + "givenName");
                    value = vf.createLiteral(givenName);
                    conn.add(subject, predicate, value, context);
                }

                if (familyName != null) {
                    predicate = vf
                            .createURI(WebidModule.FOAF_NS + "familyName");
                    value = vf.createLiteral(familyName);
                    conn.add(subject, predicate, value, context);
                }
                conn.commit();

                try {
                    StringWriter sw = new StringWriter();
                    RDFXMLWriter rdfXmlWriter = new RDFXMLWriter(sw);
                    conn.export(rdfXmlWriter, context);
                    RdfDocumentContainer rdfDocContainer = new RdfDocumentContainer();
                    rdfDocContainer.setId(context.toString());
                    rdfDocContainer.setRdfContent(sw.toString());
                    Session session = HibernateFilter.getSession(getContext(),
                            getRequest());
                    session.saveOrUpdate(rdfDocContainer);
                    session.getTransaction().commit();
                } catch (RDFHandlerException e) {
                    throw new ResourceException(e);
                } catch (HibernateException e) {
                    throw new ResourceException(e);
                }

                getResponse().redirectTemporary(context.toString());
                getResponse().setStatus(Status.SUCCESS_CREATED);

                HashMap<String, Object> data = new HashMap<String, Object>();
                data.put("redirect_url", getRequest().getResourceRef()
                        + "profile/" + localId);

                return CoryphaTemplateUtil.buildTemplateRepresentation(
                        getContext(), getRequest(),
                        "foafresourcecreated.ftl.html", data,
                        MediaType.TEXT_HTML);
            } finally {
                conn.close();
            }
        } catch (RepositoryException e) {
            throw new ResourceException(e);
        }
    }
}

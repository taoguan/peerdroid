/*
 *  Copyright (c) 2001 Sun Microsystems, Inc.  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 *  3. The end-user documentation included with the redistribution,
 *  if any, must include the following acknowledgment:
 *  "This product includes software developed by the
 *  Sun Microsystems, Inc. for Project JXTA."
 *  Alternately, this acknowledgment may appear in the software itself,
 *  if and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must
 *  not be used to endorse or promote products derived from this
 *  software without prior written permission. For written
 *  permission, please contact Project JXTA at http://www.jxta.org.
 *
 *  5. Products derived from this software may not be called "JXTA",
 *  nor may "JXTA" appear in their name, without prior written
 *  permission of Sun.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED.  IN NO EVENT SHALL SUN MICROSYSTEMS OR
 *  ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 *  USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 *  OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *  SUCH DAMAGE.
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many
 *  individuals on behalf of Project JXTA.  For more
 *  information on Project JXTA, please see
 *  <http://www.jxta.org/>.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation.
 *
 * $Id: ResolverServiceImpl.java,v 1.3 2005/06/06 00:38:32 hamada Exp $
 */
package net.jxta.impl.resolver;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import net.jxta.credential.Credential;
import net.jxta.document.Advertisement;
import net.jxta.document.MimeMediaType;
import net.jxta.document.StructuredDocument;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.XMLDocument;
import net.jxta.endpoint.ByteArrayMessageElement;
import net.jxta.endpoint.EndpointAddress;
import net.jxta.endpoint.EndpointListener;
import net.jxta.endpoint.EndpointService;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.MessageElement;
import net.jxta.endpoint.MessageTransport;
import net.jxta.endpoint.Messenger;
import net.jxta.endpoint.OutgoingMessageEvent;
import net.jxta.endpoint.OutgoingMessageEventListener;
import net.jxta.endpoint.TextDocumentMessageElement;
import net.jxta.id.ID;
import net.jxta.id.IDFactory;
import net.jxta.membership.MembershipService;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.PeerGroup;
import net.jxta.platform.Module;
import net.jxta.protocol.ModuleImplAdvertisement;
import net.jxta.protocol.ResolverQueryMsg;
import net.jxta.protocol.ResolverResponseMsg;
import net.jxta.protocol.ResolverSrdiMsg;
import net.jxta.protocol.RouteAdvertisement;
import net.jxta.rendezvous.RendezVousService;
import net.jxta.resolver.QueryHandler;
import net.jxta.resolver.ResolverService;
import net.jxta.resolver.SrdiHandler;
import net.jxta.service.Service;

import net.jxta.impl.endpoint.router.EndpointRouter;
import net.jxta.impl.endpoint.router.RouteControl;
import net.jxta.impl.protocol.ResolverQuery;
import net.jxta.impl.protocol.ResolverResponse;
import net.jxta.impl.protocol.ResolverSrdiMsgImpl;

/**
 * Implements the {@link net.jxta.resolver.ResolverService} using the standard
 * JXTA Endpoint Resolver Protocol (ERP).
 *
 * @see net.jxta.resolver.ResolverService
 * @see <a href="http://spec.jxta.org/v1.0/docbook/JXTAProtocols.html#proto-erp">JXTA Protocols Specification : Endpoint Resolver Protocol</a>
 */
public class ResolverServiceImpl implements ResolverService {

    /**
     *  Log4J Logger
     */
    private final static transient Logger LOG = Logger.getLogger(ResolverServiceImpl.class.getName());

    /**
     *  Resolver query endpoint postfix
     */
    public final static String outQueNameShort = "ORes";

    /**
     *  Resolver response endpoint postfix
     */
    public final static String inQueNameShort = "IRes";

    /**
     *  Resolver srdi endpoint postfix
     */
    public final static String srdiQueNameShort = "Srdi";

    /**
     *  MIME Type for gzipped SRDI messages.
     */
    private final static MimeMediaType GZIP_MEDIA_TYPE = new MimeMediaType("application/gzip").intern();

    private String outQueName = outQueNameShort;
    private String inQueName = inQueNameShort;
    private String srdiQueName = srdiQueNameShort;

    private String handlerName = null;
    private PeerGroup myGroup = null;
    private ModuleImplAdvertisement implAdvertisement = null;
    private EndpointService endpoint = null;
    private RendezVousService rendezvous = null;
    private MembershipService membership = null;

    private RouteControl routeControl = null;

    private final Map handlers = Collections.synchronizedMap(new HashMap(5));
    private final Map srdiHandlers = Collections.synchronizedMap(new HashMap(5));

    private Credential credential = null;
    private StructuredDocument credentialDoc = null;

    private EndpointListener queryListener = null;
    private EndpointListener responseListener = null;
    private EndpointListener srdiListener = null;

    /**
     *  the resolver interface object
     */
    private ResolverService resolverInterface = null;

    /**
     *  Convenience method for constructing an endpoint address from an id
     *
     *  @param destPeer peer id
     *  @param serv the service name (if any)
     *  @param parm the service param (if any)
     *  @param endpointAddress for this peer id.
     */
    private final static EndpointAddress mkAddress(ID destPeer, String serv, String parm) {

        EndpointAddress addr = new EndpointAddress("jxta", destPeer.getUniqueValue().toString(), serv, parm);

        return addr;
    }

    /**
     * {@inheritDoc}
     *
     */
    public void init(PeerGroup group, ID assignedID, Advertisement impl) {

        implAdvertisement = (ModuleImplAdvertisement) impl;

        myGroup = group;
        handlerName = assignedID.toString();
        String uniqueStr = myGroup.getPeerGroupID().getUniqueValue().toString();

        outQueName = uniqueStr + outQueNameShort;
        inQueName = uniqueStr + inQueNameShort;
        srdiQueName = uniqueStr + srdiQueNameShort;

        // Tell the world about our configuration.
        if (LOG.isEnabledFor(Level.INFO)) {
            StringBuffer configInfo = new StringBuffer("Configuring Resolver Service : " + assignedID);

            configInfo.append("\n\tImplementation:");
            configInfo.append("\n\t\tImpl Description: " + implAdvertisement.getDescription());
            configInfo.append("\n\t\tImpl URI : " + implAdvertisement.getUri());
            configInfo.append("\n\t\tImpl Code : " + implAdvertisement.getCode());

            configInfo.append("\n\tGroup Params:");
            configInfo.append("\n\t\tGroup: " + myGroup.getPeerGroupName());
            configInfo.append("\n\t\tGroup ID: " + myGroup.getPeerGroupID());
            configInfo.append("\n\t\tPeer ID: " + myGroup.getPeerID());

            configInfo.append("\n\tConfiguration:");
            configInfo.append("\n\t\tIn Queue name: " + outQueName);
            configInfo.append("\n\t\tOut Queue name: " + inQueName);
            configInfo.append("\n\t\tSRDI Queue name: " + srdiQueName);

            LOG.info(configInfo);
        }
    }

    /**
     * {@inheritDoc}
     */
    public int startApp(String[] arg) {
        endpoint = myGroup.getEndpointService();

        if (null == endpoint) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("Stalled until there is an endpoint service");
            }

            return Module.START_AGAIN_STALLED;
        }

        membership = myGroup.getMembershipService();

        /*
        if (null == membership) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("Stalled until there is a membership service");
            }

            return Module.START_AGAIN_STALLED;
        }
        */
        // FIXME 20040122 bondolo What if Rendezvous just happens to load
        // AFTER resolver? This seems unacceptable.
        rendezvous = myGroup.getRendezVousService();

        // Register Listeners
        try {
            // Register Query Listener
            queryListener = new DemuxQuery();
            if(!endpoint.addIncomingMessageListener(queryListener, handlerName, outQueName)) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("Cannot register listener (already registered)");
                }
            } else {
                if (null != rendezvous) {
                    if(!rendezvous.addPropagateListener(handlerName + outQueName, queryListener)) {
                        if (LOG.isEnabledFor(Level.ERROR)) {
                            LOG.error("Cannot register query listener (already registered)");
                        }
                    }
                } else {
                    if (LOG.isEnabledFor(Level.ERROR)) {
                        LOG.error("Failed to register query listener (null rendezvous service)");
                    }
                }
            }

            // Register Response Listener
            responseListener = new DemuxResponse();
            if(!endpoint.addIncomingMessageListener(responseListener, handlerName, inQueName)) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("Cannot register listener (already registered)");
                }
            } else {
                if (null != rendezvous) {
                    if(!rendezvous.addPropagateListener(handlerName + inQueName, responseListener)) {
                        if (LOG.isEnabledFor(Level.ERROR)) {
                            LOG.error("Cannot register listener (already registered)");
                        }
                    }
                } else {
                    if (LOG.isEnabledFor(Level.ERROR)) {
                        LOG.error("Failed to register response listener (null rendezvous service)");
                    }
                }
            }

            // Register SRDI Listener
            if (null != rendezvous) {
                srdiListener = new DemuxSrdi();

                if(!endpoint.addIncomingMessageListener(srdiListener, handlerName, srdiQueName)) {
                    if (LOG.isEnabledFor(Level.ERROR)) {
                        LOG.error("Cannot register listener (already registered)");
                    }
                } else {
                    if(!rendezvous.addPropagateListener(handlerName + srdiQueName, srdiListener)) {
                        if (LOG.isEnabledFor(Level.ERROR)) {
                            LOG.error("Cannot register listener (already registered)");
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (LOG.isEnabledFor(Level.ERROR)) {
                LOG.error("failed to add listeners", e);
            }

            return -1;
        }

        // FIXME tra 20031102 Until the new subscription service is implemented,
        // we use the Router Control IOCTL
        //
        // Obtain the route control object to manipulate route information when
        // sending and receiving resolver queries.
        MessageTransport endpointRouter = (MessageTransport) endpoint.getMessageTransport("jxta");
        if (endpointRouter != null) {
            routeControl = (RouteControl) endpointRouter.transportControl(EndpointRouter.GET_ROUTE_CONTROL, null);
        }
/*
        synchronized (this) {
            try {
                credential = membership.getDefaultCredential();

                if (null != credential) {
                    credentialDoc = credential.getDocument(MimeMediaType.XMLUTF8);
                } else {
                    credentialDoc = null;
                }
            } catch (Exception all) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("could not get credential", all);
                }
            }
        }
*/
        if (LOG.isEnabledFor(Level.DEBUG)) {
             LOG.debug("ResolverService Started for group :"+myGroup.getPeerGroupID().toString());
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public void stopApp() {

        endpoint.removeIncomingMessageListener(handlerName, outQueName);
        endpoint.removeIncomingMessageListener(handlerName, inQueName);

        if (rendezvous != null) {
            rendezvous.removePropagateListener(handlerName + outQueName, queryListener);
            rendezvous.removePropagateListener(handlerName + inQueName, responseListener);
        }

        if(null != srdiListener) {
            endpoint.removeIncomingMessageListener(handlerName, srdiQueName);
            rendezvous.removePropagateListener(handlerName + srdiQueName, srdiListener);
        }

        queryListener = null;
        responseListener = null;
        srdiListener = null;
        routeControl = null;
        rendezvous = null;
        membership = null;
        myGroup = null;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized Service getInterface() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Advertisement getImplAdvertisement() {
        return implAdvertisement;
    }

    /**
     * {@inheritDoc}
     */
    public QueryHandler registerHandler(String name, QueryHandler handler) {
        return (QueryHandler) handlers.put(name, handler);
    }

    /**
     * {@inheritDoc}
     */
    public QueryHandler unregisterHandler(String name) {
        return (QueryHandler) handlers.remove(name);
    }

    /**
     * given a name returns the query handler associated with it
     */
    public QueryHandler getHandler(String name) {
        return (QueryHandler) handlers.get(name);
    }

    /**
     * {@inheritDoc}
     */
    public SrdiHandler registerSrdiHandler(String name, SrdiHandler handler) {
        return (SrdiHandler) srdiHandlers.put(name, handler);
    }

    /**
     * {@inheritDoc}
     */
    public SrdiHandler unregisterSrdiHandler(String name) {
        return (SrdiHandler) srdiHandlers.remove(name);
    }

    /**
     * given a name returns the srdi handler associated with it
     */
    public SrdiHandler getSrdiHandler(String name) {
        return (SrdiHandler) srdiHandlers.get(name);
    }

    /**
     * {@inheritDoc}
     */
    public void sendQuery(String destPeer, ResolverQueryMsg query) {

        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("sending query to resolver handler: " + query.getHandlerName());
        }

        // NOTE: Add route information about the issuing peer, so the
        // resolver query responding peer can respond to the issuer without
        // requiring any route discovery. In most case the responding peer
        // is unlikely to know the route to the query issuer. This is a good
        // optimization for edge peers. This optimzation is much less
        // important for RDV peers as they are more likely to have a route
        // to peers. Also, there is the concern that adding route info
        // in resolver query exchanged between RDV will increase overhead due
        // to the larger amount of information exchanged between RDV.
        // Only update query if the query does not already contain any route
        // information. We are mostly interested in the original src
        // route information.
        if (!myGroup.isRendezvous()) {
            if (query.getSrcPeerRoute() == null) {
                if (routeControl != null) {

                    // FIXME tra 20031102 Until the new subscription service
                    // is implemented, we use the Router Control IOCTL
                    RouteAdvertisement route = routeControl.getMyLocalRoute();
                    if (route != null) {
                        query.setSrcPeerRoute((RouteAdvertisement) route.clone());
                        if (LOG.isEnabledFor(Level.DEBUG)) {
                            LOG.debug("Sending query with route info to " + route.getDestPeerID());
                        }
                    }
                }
            }
        }

        String queryHandlerName = query.getHandlerName();

        if (destPeer == null) {
            try {
                Message queryMsg = new Message();

                XMLDocument asDoc = (XMLDocument) query.getDocument(MimeMediaType.XMLUTF8);
                MessageElement docElem = new TextDocumentMessageElement(outQueName, asDoc, null);

                queryMsg.addMessageElement("jxta", docElem);

                if(null != rendezvous) {
                    // Walk the message
                    rendezvous.walk((Message) queryMsg.clone(), handlerName, outQueName, RendezVousService.DEFAULT_TTL);

                    // propagate to local net as well
                    rendezvous.propagateToNeighbors(queryMsg, handlerName, outQueName, 2);
                } else {
                    endpoint.propagate(queryMsg, handlerName, outQueName);
                }
            } catch (IOException e) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Failure during propagate", e);
                }
            }
        } else {
            // unicast instead
            try {
                boolean success = sendMessage(destPeer, handlerName, outQueName, outQueName, (XMLDocument) query.getDocument(MimeMediaType.XMLUTF8),
                                              false);
            } catch (IOException e) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Failure while unicasting query", e);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void sendResponse(String destPeer, ResolverResponseMsg response) {

        if (destPeer == null) {
            if (rendezvous == null) {
                return;
            }
            propagateResponse(response);
        } else {
            String queryHandlerName = response.getHandlerName();
            try {
                // Check if an optional route information is
                // available to send the response
                RouteAdvertisement route = response.getSrcPeerRoute();

                if (route == null) {
                    if (LOG.isEnabledFor(Level.DEBUG)) {
                        LOG.debug("No route info available to send a response");
                    }
                } else { // ok we have a route let's pass it to the router
                    if (routeControl.addRoute(route) == RouteControl.FAILED) {
                        if (LOG.isEnabledFor(Level.WARN)) {
                            LOG.warn("Failed to add route " + route.display());
                        }
                    } else {
                        if (LOG.isEnabledFor(Level.DEBUG)) {
                            LOG.debug("Add route to issuer " + route.getDestPeerID());
                        }
                    }
                }

                boolean success = sendMessage(destPeer,
                                              handlerName,
                                              inQueName,
                                              inQueName,
                                              (XMLDocument) response.getDocument(MimeMediaType.XMLUTF8),
                                              false);
            } catch (Exception e) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Error in sending response", e);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void sendSrdi(String destPeer, ResolverSrdiMsg srdi) {
        String srdiHandlerName = srdi.getHandlerName();
        if (destPeer == null) {
            if (rendezvous == null) {
                return;
            }
            Message propagateMsg = new Message();

            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPOutputStream gos = new GZIPOutputStream(baos);

                srdi.getDocument(MimeMediaType.XMLUTF8).sendToStream(gos);
                gos.finish();
                gos.close();
                MessageElement zipElem = new ByteArrayMessageElement(srdiQueName, GZIP_MEDIA_TYPE, baos.toByteArray(), null);

                propagateMsg.addMessageElement("jxta", zipElem);

                rendezvous.walk(propagateMsg, handlerName, srdiQueName, RendezVousService.DEFAULT_TTL);

                // propagate to local net as well
                rendezvous.propagateToNeighbors(propagateMsg, handlerName, srdiQueName, 2);
            } catch (IOException e) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Failure sending srdi message", e);
                }
            }
        } else {
            try {
                boolean success = sendMessage(destPeer,
                                              handlerName,
                                              srdiQueName,
                                              srdiQueName,
                                              (XMLDocument) srdi.getDocument(MimeMediaType.XMLUTF8),
                                              // compression
                                              true);
            } catch (Exception e) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Error in sending srdi message", e);
                }
            }
        }
    }

    private void repropagateQuery(Message msg, ResolverQueryMsg query) {

        if ((null != rendezvous) && !myGroup.isRendezvous()) {
            // not a Rendezvous peer? Let someone else forward it.
            return;
        }

        // just in case an excessive of attempt to forward a query
        // hopCount is used to determine forward counts independent of any other TTL
        if (query.getHopCount() > 3) {
            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("discarding ResolverQuery. HopCount exceeded : " + query.getHopCount());
            }
        }

        XMLDocument asDoc = (XMLDocument) query.getDocument(MimeMediaType.XMLUTF8);
        MessageElement docElem = new TextDocumentMessageElement(outQueName, asDoc, null);

        msg.replaceMessageElement("jxta", docElem);

        // Re-propagate the message.
        // Loop and TTL control is done in demux and propagate(). The TTL
        // below is just a default it will be reduced appropriately.

        try {
            if (null != rendezvous) {
                rendezvous.walk(msg, handlerName, outQueName, RendezVousService.DEFAULT_TTL);
                // propagate to local net as well
                rendezvous.propagateToNeighbors(msg, handlerName, outQueName, 2);
            } else {
                endpoint.propagate(msg, handlerName, inQueName);
            }
        } catch (IOException e) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("Failure propagating query", e);
            }
        }
    }

    /**
     *  Process a resolver query.
     *
     *  @param query The query.
     *  @param srcAddr  Who sent the query to us. May not be the same as the
     *  query originator.
     */
    private int processQuery(ResolverQueryMsg query, EndpointAddress srcAddr) {
        String queryHandlerName = query.getHandlerName();
        QueryHandler theHandler = getHandler(queryHandlerName);

        if (query.getHopCount() > 2) {
            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Discarding query #"+ query.getQueryId() + " hopCount > 2 : " + query.getHopCount());
            }

            // query has been forwarded too many times stop
            return ResolverService.OK;
        }

        if (theHandler == null) {
            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Discarding query #"+ query.getQueryId() + ", no handler for :" + queryHandlerName);
            }
            // If this peer is a rendezvous peer, it needs to
            // repropagate the query to other rendezvous peer that
            // may have a handler.
            return ResolverService.Repropagate;
        }

        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Handing query #"+ query.getQueryId() + " to : " + queryHandlerName);
        }

        long startTime = 0;
        try {
            int result;
            return theHandler.processQuery(query, srcAddr);
        } catch (Throwable any) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("Uncaught Throwable from handler for : " + queryHandlerName, any);
            }
            // stop repropagation
            return ResolverService.OK;
        }
    }

    /**
     *  Process a resolver response.
     *
     *  @param resp The response.
     *  @param srcAddr  Who sent the response. May not be the same as the
     *  originator response.
     */
    private void processResponse(ResolverResponseMsg resp, EndpointAddress srcAddr) {

        String handlerName = ((ResolverResponseMsg) resp).getHandlerName();
        //System.out.println("received a response from :"+srcAddr.toString());
        if (handlerName == null) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("Missing handlername in response");
            }
            return;
        }

        QueryHandler theHandler = getHandler(handlerName);
        if (theHandler == null) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("No handler for :" + handlerName);
            }
            return;
        }

        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Process response to query #" + resp.getQueryId() + " with " + handlerName);
        }

        long startTime = 0;
        try {
            theHandler.processResponse(resp, srcAddr);
        } catch (Throwable all) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("Uncaught Throwable from handler for: " + handlerName, all);
            }
        }
    }

    /**
     *  propagate a response
     *
     * @param  response  response message to propagate
     */
    private void propagateResponse(ResolverResponseMsg response) {

        Message propagateMsg = new Message();
        String queryHandlerName = response.getHandlerName();
        try {

            XMLDocument responseDoc = (XMLDocument) response.getDocument(MimeMediaType.XMLUTF8);
            MessageElement elemDoc = new TextDocumentMessageElement(inQueName, responseDoc, null);

            propagateMsg.addMessageElement("jxta", elemDoc);

            if(null != rendezvous) {
                rendezvous.walk(propagateMsg, handlerName, inQueName, RendezVousService.DEFAULT_TTL);
            } else {
                endpoint.propagate(propagateMsg, handlerName, inQueName);
            }
        } catch (IOException e) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("failure during propagateResponse", e);
            }
        }
    }

    /**
     *  Process an SRDI message.
     *
     *  @param srdimsg The SRDI message.
     *  @param srcAddr  Who sent the message. May not be the same as the
     *  originator of the message.
     */
    private void processSrdi(ResolverSrdiMsgImpl srdimsg, EndpointAddress srcAddr) {
        String handlerName = srdimsg.getHandlerName();

        if (handlerName == null) {
            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("Missing handlername in response");
            }
            return;
        }

        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Processing an SRDI msg for : " + handlerName);
        }

        SrdiHandler theHandler = getSrdiHandler(handlerName);

        if (theHandler != null) {
            try {
                long startTime = 0;
                theHandler.processSrdi(srdimsg);
            } catch (Throwable all) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Uncaught Throwable from handler for: " + handlerName, all);
                }
            }
        } else {
            if (LOG.isEnabledFor(Level.WARN) && !myGroup.isRendezvous()) {
                LOG.warn("No srdi handler registered :" + handlerName);
            } else if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("No srdi handler registered :" + handlerName);
            }
        }
    }


    /**
     * Send a resolver message to a peer
     *
     * @param  destPeer destination peer
     * @param  pName service name on the destination
     * @param  pParam service param on the destination
     * @param  tagName tag name of the message element
     * @param  response the body of the message element
     * @param  gzip If <code>true</code> then encode the message body using gzip.
     */
    private boolean sendMessage(String destPeer,
                                String pName,
                                String pParam,
                                String tagName,
                                XMLDocument response,
                                boolean gzip) throws IOException {

        // Get the messenger ready
        ID dest;
        try {
            dest = IDFactory.fromURI(new URI(destPeer));
        } catch (URISyntaxException badpeer) {
            IOException failure = new IOException("bad destination peerid");
            failure.initCause(badpeer);
            throw failure;
        }

        EndpointAddress destAddress = mkAddress(dest, pName, pParam);

        // FIXME add route to responses as well
        Messenger messenger = endpoint.getMessengerImmediate(destAddress, null);

        // Build the Message
        Message msg = new Message();
        try {
            MessageElement msgEl;
            if (gzip) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPOutputStream gos = new GZIPOutputStream(baos);
                response.sendToStream(gos);
                gos.finish();
                gos.close();
                msgEl = new ByteArrayMessageElement(tagName, GZIP_MEDIA_TYPE, baos.toByteArray(), null);
            } else {
                msgEl = new TextDocumentMessageElement(tagName, response, null);
            }
            msg.addMessageElement("jxta", msgEl);
        } catch (Exception ez1) {
            // Not much we can do
            if (LOG.isEnabledFor(Level.ERROR)) {
                LOG.error("Failed building message", ez1);
            }
            return false;
        }

        // Send the message
        if (LOG.isEnabledFor(Level.DEBUG)) {
            LOG.debug("Sending " + msg + " to " + destAddress + " " + tagName);
        }

        if (null != messenger) {
            // XXX 20040924 bondolo Convert this to ListenerAdaptor
            messenger.sendMessage(msg, null, null, new FailureListener(dest));
            return true;
        } else {
            return false;
        }
    }

    /**
     *   Inner class to handle incoming queries
     */
    private class DemuxQuery implements EndpointListener {

        /**
         * {@inheritDoc}
         */
        public void processIncomingMessage(Message message, EndpointAddress srcAddr, EndpointAddress dstAddr) {

            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Demuxing a query message from " + srcAddr);
            }

            MessageElement element = message.getMessageElement("jxta", outQueName);

            if (element == null) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Message does not contain a query. Discarding message");
                }
                return;
            }

            ResolverQueryMsg query;

            try {
                StructuredDocument asDoc = StructuredDocumentFactory.newStructuredDocument(element.getMimeType(), element.getStream());
                query = new ResolverQuery(asDoc);
            } catch (IOException e) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Ill formatted resolver query, ignoring.", e);
                }
                return;
            } catch (IllegalArgumentException e) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Ill formatted resolver query, ignoring.", e);
                }
                return;
            }

            int res = processQuery(query, srcAddr);

            if (ResolverService.Repropagate == res) {
                if (LOG.isEnabledFor(Level.DEBUG)) {
                    LOG.debug("Repropagating query " +  message + " from " + srcAddr);
                }
                repropagateQuery(message, query);
            }
        }
    }


    /**
     * Inner class to handle incoming responses
     */
    private class DemuxResponse implements EndpointListener {

        /**
         *  @inheritDoc
         */
        public void processIncomingMessage(Message message, EndpointAddress srcAddr, EndpointAddress dstAddr) {

            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Demuxing a response from " + srcAddr);
            }

            MessageElement element = (MessageElement) message.getMessageElement("jxta", inQueName);

            if (null == element) {
                if (LOG.isEnabledFor(Level.DEBUG)) {
                    LOG.debug("Message does not contain a response. Discarding message");
                }
                return;
            }

            ResolverResponse resp;

            try {
                StructuredDocument asDoc = StructuredDocumentFactory.newStructuredDocument(element.getMimeType(), element.getStream());
                resp = new ResolverResponse(asDoc);
            } catch (IOException e) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Ill formatted resolver response, ignoring.", e);
                }
                return;
            } catch (IllegalArgumentException e) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Ill formatted resolver response, ignoring.", e);
                }
                return;
            }
            processResponse(resp, srcAddr);
        }
    }

    /**
     *  Inner class to handle SRDI messages
     */
    private class DemuxSrdi implements EndpointListener {

        /**
         *  @inheritDoc
         */
        public void processIncomingMessage(Message message, EndpointAddress srcAddr, EndpointAddress dstAddr) {

            if (LOG.isEnabledFor(Level.DEBUG)) {
                LOG.debug("Demuxing an SRDI message from : " + srcAddr);
            }

            MessageElement element = (MessageElement) message.getMessageElement("jxta", srdiQueName);

            if (element == null) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Message does not contain a SRDI element. Discarding message");
                }
                return;
            }

            ResolverSrdiMsgImpl srdimsg = null;

            try {
                if (element.getMimeType().equals(GZIP_MEDIA_TYPE)) {
                    InputStream gzipStream = new GZIPInputStream(element.getStream());
                    StructuredDocument asDoc = StructuredDocumentFactory.newStructuredDocument(MimeMediaType.XMLUTF8, gzipStream);
                    srdimsg = new ResolverSrdiMsgImpl(asDoc, membership);
                } else {
                    StructuredDocument asDoc = StructuredDocumentFactory.newStructuredDocument(element.getMimeType(), element.getStream());
                    srdimsg = new ResolverSrdiMsgImpl(asDoc, membership);
                }
            } catch (IOException e) {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Ill formatted SRDI message, ignoring.", e);
                }
                return;
            }
            processSrdi(srdimsg, srcAddr);
        }
    }

    /**
     *  Listener to find bad destinations and clean srdi tables for them.
     */
    class FailureListener implements OutgoingMessageEventListener {

        final ID dest;

        FailureListener(ID dest) {
            this.dest = dest;
        }

        /**
         *  {@inheritDoc}
         */
        public void messageSendFailed(OutgoingMessageEvent event) {
            // Ignore the failure if it's a case of queue overflow.
            if (event.getFailure() == null) {
                return;
            }

            if (LOG.isEnabledFor(Level.WARN)) {
                LOG.warn("Clearing SRDI tables for failed peer : " + dest);
            }

            Iterator it = Arrays.asList(srdiHandlers.values().toArray()).iterator();

            while (it.hasNext()) {
                SrdiHandler theHandler = (SrdiHandler) it.next();

                try {
                    theHandler.messageSendFailed((PeerID) dest, event);
                } catch (Throwable all) {
                    if (LOG.isEnabledFor(Level.WARN)) {
                        LOG.warn("Uncaught Throwable from handler : " + theHandler, all);
                    }
                }
            }
        }

        /**
         *  {@inheritDoc}
         */
        public void messageSendSucceeded(OutgoingMessageEvent event) {
            // great!
        }
    }
}

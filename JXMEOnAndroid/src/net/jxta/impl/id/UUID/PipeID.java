/*
 *
 * $Id: PipeID.java,v 1.1 2005/05/03 06:50:00 hamada Exp $
 *
 * Copyright (c) 2001 Sun Microsystems, Inc.  All rights reserved.
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
 *       Sun Microsystems, Inc. for Project JXTA."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA"
 *    must not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact Project JXTA at http://www.jxta.org.
 *
 * 5. Products derived from this software may not be called "JXTA",
 *    nor may "JXTA" appear in their name, without prior written
 *    permission of Sun.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL SUN MICROSYSTEMS OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of Project JXTA.  For more
 * information on Project JXTA, please see
 * <http://www.jxta.org/>.
 *
 * This license is based on the BSD license adopted by the Apache Foundation.
 */

package net.jxta.impl.id.UUID;

import java.net.URI;
import java.net.URL;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;

/**
 *  An implementation of the {@link net.jxta.pipe.PipeID} ID Type.
 */
public class PipeID extends net.jxta.pipe.PipeID {

    /**
     *  Log4J categorgy
     */
    private static final transient Logger LOG = Logger.getLogger(PipeID.class.getName());

    protected final static int groupIdOffset = 0;
    protected final static int idOffset = PipeID.groupIdOffset + IDFormat.uuidSize;
    protected final static int padOffset = PipeID.idOffset + IDFormat.uuidSize;
    protected final static int padSize = IDFormat.flagsOffset - PipeID.padOffset;

    /**
     *  The id data
     */
    protected IDBytes id;

    /**
     *  Used only internally
     *
     */
    protected PipeID() {
        super();
        id = new IDBytes();
        id.bytes[IDFormat.flagsOffset + IDFormat.flagsIdTypeOffset] = IDFormat.flagPipeID;
    }

    /**
     * Constructor.
     * Intializes contents from provided ID.
     *
     *
     * @param id    the ID data
     */
    protected PipeID(IDBytes id) {
        super();
        this.id = id;
    }

    /**
     * Creates a PipeID. A PeerGroupID is provided
     *
     * @param groupUUID    the UUID of the group to which this will belong.
     * @param idUUID    the UUID which will be used for this pipe.
     */
    protected PipeID(UUID groupUUID,  UUID idUUID) {
        this();

        id.longIntoBytes(
            PipeID.groupIdOffset, groupUUID.getMostSignificantBits());
        id.longIntoBytes(
            PipeID.groupIdOffset + 8, groupUUID.getLeastSignificantBits());

        id.longIntoBytes(
            PipeID.idOffset, idUUID.getMostSignificantBits());
        id.longIntoBytes(
            PipeID.idOffset + 8, idUUID.getLeastSignificantBits());
    }

    /**
     *  See {@link net.jxta.id.IDFactory.Instantiator#newPipeID(net.jxta.peergroup.PeerGroupID)}.
     */
    public PipeID(PeerGroupID groupID) {
        this(groupID.getUUID(), UUIDFactory.newUUID());
    }

    /**
     *  See {@link net.jxta.id.IDFactory.Instantiator#newPipeID(net.jxta.peergroup.PeerGroupID,byte[])}.
     */
    public PipeID(PeerGroupID groupID, byte [] seed) {
        this();

        UUID groupUUID = groupID.getUUID();

        id.longIntoBytes(
            PipeID.groupIdOffset, groupUUID.getMostSignificantBits());
        id.longIntoBytes(
            PipeID.groupIdOffset + 8, groupUUID.getLeastSignificantBits());

        for(int copySeed = Math.min(IDFormat.uuidSize, seed.length) - 1;
            copySeed >= 0;
            copySeed--)
            id.bytes[copySeed + PipeID.idOffset] = seed[copySeed];

        // make it a valid UUID
        id.bytes[PipeID.idOffset + 6] &= 0x0f;
        id.bytes[PipeID.idOffset + 6] |= 0x40; /* version 4 */
        id.bytes[PipeID.idOffset + 8] &= 0x3f;
        id.bytes[PipeID.idOffset + 8] |= 0x80; /* IETF variant */
        id.bytes[PipeID.idOffset + 10] &= 0x3f;
        id.bytes[PipeID.idOffset + 10] |= 0x80; /* multicast bit */
    }

    /**
     *  {@inheritDoc}
     */
    public boolean equals(Object target) {
        if (this == target) {
            return true;
        }

        if (target instanceof PipeID) {
            PipeID pipeTarget = (PipeID)target;

            if(!getIDFormat().equals(pipeTarget.getIDFormat()))
                return false;

            if(id == pipeTarget.id)
                return true;

            // if true then we can have the two ids share the id bytes
            boolean result = id.equals(pipeTarget.id);

            if(result)
                pipeTarget.id = id;

            return result;
        } else
            return false;
    }

    /**
     *  {@inheritDoc}
     */
    public int hashCode() {
        return id.hashCode();
    }

    /**
     *  {@inheritDoc}
     */
    public String getIDFormat() {
        return IDFormat.INSTANTIATOR.getSupportedIDFormat();
    }

    /**
     *  {@inheritDoc}
     */
    public Object getUniqueValue() {
        return getIDFormat() + "-" + (String) id.getUniqueValue();
    }


    /**
     *  {@inheritDoc}
     */
    public net.jxta.id.ID getPeerGroupID() {
        UUID groupUUID = new UUID(
                             id.bytesIntoLong(PipeID.groupIdOffset),
                             id.bytesIntoLong(PipeID.groupIdOffset + 8));

        PeerGroupID groupID = new PeerGroupID(groupUUID);

        // convert to the generic world PGID as necessary
        return IDFormat.translateToWellKnown(groupID);
    }

    /**
     *  {@inheritDoc}
     */
    public URI toURI() {
        return IDFormat.toURI((String) getUniqueValue());
    }
}

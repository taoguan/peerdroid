/*
 *  $Id: SimpleSelectable.java,v 1.2 2005/06/13 15:21:32 hamada Exp $
 *
 *  Copyright (c) 2004 Sun Microsystems, Inc.  All rights reserved.
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
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA"
 *  must not be used to endorse or promote products derived from this
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
 *
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many
 *  individuals on behalf of Project JXTA.  For more
 *  information on Project JXTA, please see
 *  <http://www.jxta.org/>.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation.
 */
package net.jxta.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 *  This the interface that all selectable objects expose.<p/>
 *
 *  Applications programmers should treat this API as temporary, for now.<p/>
 *
 *  A SimpleSelectable object can register SimpleSelector objects so that they
 *  are notified whenever this object choses to report a change.<p/>
 *
 *  SimpleSelectors are SimpleSelectable, therefore selectors can be selected.
 *  <p/>
 *
 *  The change notification interface used to notify a selector is actually
 *  specified in SimpleSelectable. As a result, certain implementations may also
 *  allow to register SimpleSelectables that are not Selectors. Selectors
 *  themselves do not allow that.<p/>
 *
 *
 *
 *@see    SimpleSelector
 *@see    AbstractSimpleSelectable
 */

public interface SimpleSelectable {

    /**
     *  A simple reference object that can be put in a map instead of the one it
     *  refers to. SimpleSelectable object often need to be put in maps where
     *  distinct objects are to be treated as such, even if they are identical
     *  at a semantical level. However, some SimpleSelectable objects may have
     *  semantical equals and hashcode methods rather than the identity ones.
     *  For that reason, whenever a SimpleSelectable needs to be used as a map
     *  or set key, its identity reference should be used instead. All
     *  SimpleSelectable can return an identity reference. A given
     *  SimpleSelectable always provides the same IdentityReference object.
     *  IdentityReference never overloads hashcode and equals in a way that
     *  could make different objects be equal or that could provide different
     *  results from invocation to invocation.
     */
    public static class IdentityReference {
        private Object object;

        /**
         *@param  object  Description of the Parameter
         */
        public IdentityReference(Object object) {
            this.object = object;
        }

        /**
         *@return    The object that this one refers to.
         */
        public Object getObject() {
            return object;
        }
    }

    /**
     *@return    A canonical IdentityReference for this object. A given
     *      SimpleSelectable always provides the same IdentityReference object.
     *      An IdentityReference must never overload hashcode and equals in a
     *      way that could make different objects be equal or that could provide
     *      different results from invocation to invocation.
     */
    public IdentityReference getIdentityReference();

    /**
     *  Registers the given selector with this selectable object. This always
     *  causes one change event for this object to be reported through the
     *  selector. As a result, when selecting for a condition, it is not
     *  necessary to verify whether it has already happened or not; the next
     *  call to select will be able to detect it.
     *
     *@param  s  The SimpleSelector to register
     */
    public void register(SimpleSelector s);

    /**
     *  Unregisters the given selector, so that it is no-longer notified when
     *  this object changes.
     *
     *@param  s  The SimpleSelector to unregister
     */
    public void unregister(SimpleSelector s);

    /**
     *  This method is invoked when the given selectable object has changed.
     *  This permits to cascade selectable objects, so that one reports a change
     *  when the other changes, without having to select it. This Also permits
     *  to implement this interface by delegating its implementation to a
     *  utility class. <p/>
     *
     *@param  changedObject  the object that has changed.
     *@see                   AbstractSimpleSelectable
     */
    public void itemChanged(SimpleSelectable changedObject);
}


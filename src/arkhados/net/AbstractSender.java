/*
 * Copyright (c) 2009-2011 William Linna
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'Arkhados' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package arkhados.net;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.network.HostedConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public abstract class AbstractSender  extends AbstractAppState
        implements Sender {

    protected static final Logger logger =
            Logger.getLogger(AbstractSender.class.getName());

    static {
        logger.setLevel(Level.INFO);
    }
    private int otmIdCounter = 0;
    private boolean shouldSend;
    private Application app;

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);
        this.app = app;
    }

    protected OneTrueMessage createOneTrueMessage(HostedConnection connection) {
        List<OtmIdCommandListPair> unconfirmedGuaranteed =
                getGuaranteedForSource(connection);
        List<Command> enqueuedGuaranteed =
                getEnqueuedGuaranteedForSource(connection);
        List<Command> enqueuedUnreliables =
                getEnqueuedUnreliablesForSource(connection);

        OneTrueMessage otm = new OneTrueMessage(otmIdCounter);

        if (!enqueuedGuaranteed.isEmpty()) {
            unconfirmedGuaranteed.add(new OtmIdCommandListPair(otmIdCounter,
                    new ArrayList<>(enqueuedGuaranteed)));
        }

        if (!unconfirmedGuaranteed.isEmpty()) {
            otm.getGuaranteed().addAll(unconfirmedGuaranteed);
        }
        if (!enqueuedUnreliables.isEmpty()) {
            otm.getUnreliables().addAll(enqueuedUnreliables);
        }

        enqueuedGuaranteed.clear();
        enqueuedUnreliables.clear();

        return otm;
    }

    private void confirmAllUntil(Object source, int until) {
        List<OtmIdCommandListPair> listToRemoveFrom =
                getGuaranteedForSource(source);

        for (Iterator<OtmIdCommandListPair> it = listToRemoveFrom.iterator();
                it.hasNext();) {
            OtmIdCommandListPair otmIdCommandListPair = it.next();
            if (otmIdCommandListPair.getOtmId() <= until) {
                it.remove();
            } else if (otmIdCommandListPair.getOtmId() > until) {
                break;
            }
        }
    }

    @Override
    public void update(float tpf) {
        super.update(tpf);

        if (!shouldSend) {
            return;
        }

        sendMessage();

        ++otmIdCounter;

        shouldSend = false;
    }

    @Override
    public void readGuaranteed(Object source, Command guaranteed) {
    }

    @Override
    public void readUnreliable(final Object source, final Command unreliable) {
        if (unreliable instanceof Ack) {
            app.enqueue(() -> {
                Ack ack = (Ack) unreliable;
                confirmAllUntil(source, ack.getConfirmedOtmId());
                return null;
            });
        }
    }

    protected abstract List<OtmIdCommandListPair> getGuaranteedForSource(
            Object source);

    protected abstract List<Command> getEnqueuedGuaranteedForSource(
            HostedConnection connection);

    protected abstract List<Command> getEnqueuedUnreliablesForSource(
            HostedConnection connection);

    @Override
    public void setShouldSend(boolean shouldSend) {
        this.shouldSend = shouldSend;
    }
}

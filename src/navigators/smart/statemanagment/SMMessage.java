/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.statemanagment;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.SerialisationHelper;


/**
 * This classe represents a message used in the state transfer protocol
 * 
 * @author Jo�o Sousa
 */
public class SMMessage extends SystemMessage {

    private TransferableState state; // State log
    private long eid; // Execution ID up to which the sender needs to be updated
    private int type; // Message type
    private int replica; // Replica that should send the state

    /**
     * Constructs a SMMessage
     * @param sender Process Id of the sender
     * @param eid Execution ID up to which the sender needs to be updated
     * @param type Message type
     * @param replica Replica that should send the state
     * @param state State log
     */
    public SMMessage(int sender, long eid, int type, int replica, TransferableState state) {

        super(Type.SM_MSG, sender);
        this.state = state;
        this.eid = eid;
        this.type = type;
        this.replica = replica;
    }

    public SMMessage(DataInput in) throws IOException, ClassNotFoundException {
        super(Type.SM_MSG, in);
        eid = in.readLong();
        type = in.readInt();
        replica = in.readInt();
        state = (TransferableState) SerialisationHelper.readObject(in);
    }
    /**
     * Retrieves the state log
     * @return The state Log
     */
    public TransferableState getState() {
        return state;
    }

    /**
     * Retrieves the type of the message
     * @return The type of the message
     */
    public int getType() {
        return type;
    }

    /**
     * Retrieves the execution ID up to which the sender needs to be updated
     * @return The execution ID up to which the sender needs to be updated
     */
    public long getEid() {
        return eid;
    }

    /**
     * Retrieves the replica that should send the state
     * @return The replica that should send the state
     */
    public int getReplica() {
        return replica;
    }

    @Override
    public void serialise(DataOutput out) throws IOException{
        super.serialise(out);
        out.writeLong(eid);
        out.writeInt(type);
        out.writeInt(replica);
        SerialisationHelper.writeObject(state,out);
    }

}

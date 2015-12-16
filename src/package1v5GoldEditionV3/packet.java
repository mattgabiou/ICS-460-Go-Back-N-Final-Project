package package1v5GoldEditionV3;
import java.nio.*;

public class packet{
	//arbitrary checksum
	 short cksum = 1111;
	 //length of packet and identifying feature (between acks and data)
	 short len;
	 //number of acknowledgement
	 int ackno;
	 //number of sequence
	 int seqno = -1;
	 ///the data held by this packet
	 byte[] data = null;
	public packet(int ackno){
		//if all that's supplied is an ack number then it's an ack packet
		this.ackno = ackno % 64;
		//ackpackets are going to be 8 bytes no matter what
		this.len = (8);
	}
	public packet(int ackno, int seqno, byte[] data){
		//if sequence and data are also supplied then it's a data packet
		this.ackno = ackno;
		this.seqno = seqno;
		this.data = data;
		//a data packets length is 12 + the length of its payload
		this.len = (short) (data.length + 12);
	}
	
	public short getCksum() {
		return cksum;
	}
	public short getLen() {
		return len;
	}
	public int getAckno() {
		return ackno;
	}
	public int getSeqno() {
		return seqno;
	}
	public byte[] getData() {
		return data;
	}
	/**
	 * return type 0 is an Ack packet
	 * return type 1 is a data packet
	 * return type 2 is an End of Transmission packet
	 * @return
	 */
	public int getType(){
		// end of transmission packets are assigned a length of -1 manually
		if(len == -1){
			return 2;
		}
		//if the length of a packet is less than 12 it's an ack
		if (len < 12){
			return 0;
		}else{
			//if it isn't it's a data
			return 1;
		}
	}
	/**
	 * For creating a byte array from a packet
	 * @return
	 */
	public  byte[] getUDPdata(){
		//Creat a byte buffer for byte intake
		ByteBuffer buffer = null;
		if(data != null){
			//we allocate its size based upon the length of data + the number 
			buffer = ByteBuffer.allocate(data.length + 12);
			// of bytes needed for length cksum ackno and seqno
			buffer.putShort(cksum);
			buffer.putShort(len);
			buffer.putInt(ackno);
			buffer.putInt(seqno);
			buffer.put(data);
		}else{
			//being that only data is variable in length acks will always be 8
			buffer = ByteBuffer.allocate(8);
			buffer.putShort(cksum);
			buffer.putShort(len);
			buffer.putInt(ackno);
		}
		//retrieve an array of bytes from what we've stuffed in the byte buffer
		return buffer.array();
	}
	/**
	 * This method is for creating a packet from the byte array this packet
	 * class creates
	 * @param datas
	 * @return
	 */
	public static packet parseUDPdata(byte[] datas){
		//use the byte array to back a ByteBuffer
		ByteBuffer buffer = ByteBuffer.wrap(datas);
		//we'll need at most 500 bytes
		byte[] data = new byte[500];
		//the first item in the buffer should be cksum, a short
		if(buffer.getShort() == 1111){
			//the next item should be our length, als a short
			short length = buffer.getShort();
			//this is how we decide what kind of packet we have
			if(length > 8){
				//if there're more than 8 bytes of length its a data packet
				int ack = buffer.getInt();
				int seq = buffer.getInt();
				buffer.get(data);
				//create a data packet with what we have taken
				packet packet = new packet(ack, seq, data);
				//make certain our length is what it should be
				packet.len = length;
				//return our packet
				return packet;
			}else{
				//all we need is ack
				int ack = buffer.getInt();
				//create an ack packet from it
				packet packet = new packet(ack);
				//if length was -1 we have an end of transmission packet
				if(length == -1){
					packet.len = -1;
				}
				return packet;
			}
		}
		return null;
	}
	public static packet createEOT(int nextSeqNum) {
		//create what would ordinarily be an ack packet
		packet eot = new packet(nextSeqNum);
		//set its length to -1 to mark it as an end of transmission packet
		eot.len = -1;
		return eot;
	}
}

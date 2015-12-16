package package1v5GoldEditionV3;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;


/**
 * This class plays the role of the server and receives the file sent by the 
 * sender aka client. It listens on a predefined port and acknowledges packets
 * as long as they are sent in order. It writes the text data to an output file.
 */
public class receiver {
    
    private static int modulo = 64;
    private static int errorRate = 0;
    private static int randomNumber = 999;
    private InetAddress targetHostName;
    private int theSenderPort;
    private static FileOutputStream fileOutStream, packetArrival;
    private static DatagramSocket receiverSocket;
    
    // Used for gui input
    static BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    
    // data array used to store the UDP data of the received packets.
    private byte[] packetBuffer = new byte[512];

    // the expected number of the received packet. use modulo on this
    private int nextSeqNum = 0;

    // 0/1 represents the file writing has been finished or not. 1 means finished.
    private int endOfFile = 0;

    public receiver(InetAddress address, Integer port1, Integer port2, File file)
            throws IOException {

        targetHostName = address;
        theSenderPort = port1;
    }

    // method to receive and send packets
    // TODO Listen for packets
    private void start() throws Exception {
        while (endOfFile != 1) {
        	//cleans the byte buffer in case the received data is smaller than the previous one.
            packetBuffer = new byte[512];

            // use Datagram Packet to receive packets from sender through the
            // Datagram Socket
            DatagramPacket receiverPacket = new DatagramPacket(packetBuffer,
                    packetBuffer.length);
            try {
                receiverSocket.receive(receiverPacket);
            } catch (IOException e) {
                System.out.println("Receiver Socket I/O exception!!!");
            }
            //creates a packet from the bytes received
            packet p = packet.parseUDPdata(packetBuffer);
            
            // Announce arrival of packet from sender
            String timeStampArrivePacket = new SimpleDateFormat("HH.mm.ss").format(new Date());
            System.out.println("[" + timeStampArrivePacket + "] Received packet: " + p.getSeqno());
            
            // Write to file that packet from sender arrived
            packetArrival.write((Integer.toString(p.getSeqno()) + '\n').getBytes());

             //Delay for demonstration
            try{
                TimeUnit.MILLISECONDS.sleep(2000);
            }
            catch (InterruptedException e) {
                System.out.println(e);
            }

            // 3 types of packets, determine which one it is. Really only
            // 2 exist as the receiver should not get ACKs. (1)Data packet and (2)EOT
            switch (p.getType()) {
            case 0:
                // ACK packets here should be invalid
                System.out.println("Invalid packet type (ACKs) received!!!");
                break;
            case 1:
                // length of the data packets should be larger than 0
                if (p.getData().length < 0) {
                    System.out
                    .println("Invalid packet length for data received!!!");
                    break;
                } else {
                    // if it's the expected packet write it to the file and
                    // update expected sequenceNumber
                    if (p.getSeqno() == nextSeqNum) {
                        writeToFile(p);
                        // Send an ACK packet because its the next seq #
                        System.out.println("Attempting to send ACK packet for correct sequence # packet: " + p.getSeqno() % modulo);
                        sendACK(p.getSeqno() % modulo);
                        nextSeqNum++;
                        nextSeqNum = nextSeqNum % modulo; // Wrap around for modulo                 
                        break;
                    } else {
                        // Got a data packet, but its not the next one in sequence
                        System.out.println("Warning! Received packet out of sequence! Expected packet number: " + nextSeqNum + " but got: " + p.getSeqno());

                        if (nextSeqNum == 0)
                            break;
                        else{
                            // Send ACK to sender, but its not the packet we need
                            System.out.println("Attempting to send ACK packet for Out of Order packet: " + p.getSeqno() % modulo);
                            sendACK(p.getSeqno() % modulo);
                        }

                        break;
                    }
                }
            case 2:
                // if it's the expected packet means it's the end of file
                // send EOT to the sender and close the file output stream
                // update endOfFile to break the while loop
                if (p.getAckno() == nextSeqNum % modulo) {
                    endOfFile = 1;
                    sendEOT(p.getSeqno());
                    fileOutStream.close();
                    break;
                } else {
                    // if it's not just send the ACK packet with the SeqNum
                    // of the packet before the expected one
                    if (nextSeqNum == 0)
                        break;
                    else
                        sendACK(nextSeqNum % modulo - 1);
                    break;
                }

            }

        }
        // close the Datagram Socket
        receiverSocket.close();
    }

    // send EOT to the sender using Datagram Packet
    private void sendEOT(int seqNum) throws Exception {
        packet eot = packet.createEOT(seqNum);
        byte[] eotBuffer = new byte[512];
        eotBuffer = eot.getUDPdata();
        DatagramPacket eotPacket = new DatagramPacket(eotBuffer,
                eotBuffer.length, targetHostName, theSenderPort);
        receiverSocket.send(eotPacket);
        System.out.println(" -Sending EOT packet: " +seqNum + " to sender!");
        System.out.println("");
        System.out.println("Finished receiving file, terminating receiver...");
    }

    // send the ACKs to the sender using Datagram Packet
    private void sendACK(int seqNum) throws Exception {

        // Method called with this parameter
        //System.out.println("sendAck called with this seqNum: " + seqNum);

        // Introduce errors
        // Process packet resends with error rate 
        if(errorRate > 0){
            Random randomGenerator = new Random();
            randomNumber = randomGenerator.nextInt(100);
            //System.out.println("RandomNumber is: " + randomNumber);
        }       

        // Check if ack fails
        if(randomNumber <= errorRate){
            // Lose the ACK to sender
            packet ack = new packet(seqNum);
            byte[] ackBuffer = new byte[512];
            ackBuffer = ack.getUDPdata();
            @SuppressWarnings("unused")
            DatagramPacket ackPacket = new DatagramPacket(ackBuffer,
                    ackBuffer.length, targetHostName, theSenderPort);
            // Dont actually send the ack
            //receiverSocket.send(ackPacket);
            System.out.println(" *Losing ACK to sender for packet: " + seqNum);
            System.out.println("");

            // Delay for demonstration
//            try{
//                TimeUnit.MILLISECONDS.sleep(2000);
//            }
//            catch (InterruptedException e) {
//                System.out.println(e);
//            }
        }
        else{
            // Send the ack to sender correctly
            packet ack = new packet(seqNum);
            byte[] ackBuffer = new byte[512];
            ackBuffer = ack.getUDPdata();
            DatagramPacket ackPacket = new DatagramPacket(ackBuffer,
                    ackBuffer.length, targetHostName, theSenderPort);
            receiverSocket.send(ackPacket);
            System.out.println("Correctly sending ACK to sender for packet: " + seqNum);
            System.out.println("");

            // Delay for demonstration
//            try{
//                //TimeUnit.MILLISECONDS.sleep(2000);
//                TimeUnit.MILLISECONDS.sleep(1);
//            }
//            catch (InterruptedException e) {
//                System.out.println(e);
//            }
        }
    }

    // use output stream to write packets' data to the specified file
    private void writeToFile(packet p) {
        try {
            System.out.println("Writing to file the data contents of: " + p.getSeqno());
            //Create string from the bytes in our get data
            String trimmer = new String(p.getData());
            //trims the empty spaces added as padding
            trimmer = trimmer.substring(0,p.getLen() - 12);
            //sends the bytes representing that new string
            fileOutStream.write(trimmer.getBytes());
        } catch (IOException e) {
            System.out.println("I/O exception while writing to file!!!");
        }
    }

    public static void main(String args[]) throws IOException, Exception {

        // arguments checking
        //        if (args.length != 4) {
        //            System.out.println("Please enter valid arguments!!!");
        //            System.exit(1);
        //        }
        
        // Bring up the gui menu
        menu();
        
        // Announce that the server is running
        System.out.print("Receiver is running on: ");

        // Manually Enter
        InetAddress hostAddress = InetAddress.getLocalHost();
        System.out.println(hostAddress);
        System.out.println("ACK packet error rate is: " + errorRate + "%");
        // The ports must match on both receiver and sender
        Integer senderPort = 9874;
        Integer receiverPort = 9875;
        File writtenFile = new File("output.txt");
        
        

        // Testing Command Line Arguments
        //        InetAddress hostAddress = InetAddress.getByName(args[0]);
        //        Integer emulatorPort = Integer.parseInt(args[1]);
        //        Integer receiverPort = Integer.parseInt(args[2]);
        //        File writtenFile = new File(args[3]);

        // make sure the file can be output
        if (!writtenFile.exists()) {
            System.out.println("output.txt does file not exist! New file created!");
            writtenFile.createNewFile();
        }
        if (!writtenFile.canWrite()) {
            System.out.println("output.txt file not writable!");
            System.exit(3);
        }
        System.out.println("");
        System.out.println("");

        // receiver constructor
        receiver newReceiver = new receiver(hostAddress, senderPort,
                receiverPort, writtenFile);
        
        // Listen on the port specified above
        receiverSocket = new DatagramSocket(receiverPort);

        // Output files
        fileOutStream = new FileOutputStream(writtenFile);
        packetArrival = new FileOutputStream("RCV-arrived.log");

        // start to recieve
        newReceiver.start();
        // closes the logging file for what packets have arrived
        packetArrival.close();

    }

    public void setModulo(int mod){
        modulo = mod;
    }
    
    public static void setErrorRate(){
        errorRate = Integer.parseInt(stringReader("Please enter the rate of errors (e.g. 30)  \n"));
    }
    
 // This method detects what number the user selects
    public static void menu() {
        int selection = 0;

        do {
            help();
            selection = intReader();

            switch (selection) {
            case 0:
                System.exit(0);
                break;
            case 1:
                selection = 0;
                break;
            case 2:
                setErrorRate();
                break; 
            case 13:
                System.out.println();
                help();
                System.out.println();
                break;
            }

        } while (selection != 0);
    }

    // This method reads a user input String
    public static String stringReader(String prompt) {
        System.out.print(prompt);
        String string = null;
        try {
            string = input.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return string;
    }

    // This method reads and checks for an int input within the menu list
    public static int intReader() {
        do{
            try {
                int value = Integer.parseInt(stringReader("Enter a number\n"));
                if (0 <= value && value <= 13){
                    return value;
                }
            } catch (NumberFormatException nfe){
                System.out.println("\nYou did not enter a number\n");
            }
        }while(true);
    }

    // This creates a menu for the user to choose from
    public static void help() {
        System.out.println("Receiver: Make a selection");
        System.out.println("   1 Run Program");
        System.out.println("   2 Set Error Rate");
        System.out.println("  13 Help");
        System.out.println("   0 Exit");
    }

}// end receiver class
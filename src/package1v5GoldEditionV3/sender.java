package package1v5GoldEditionV3;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * This class plays the role of the client aka sender and sends a text file to the receiver.
 * It listens on a predefined port and get ACK packets from the receiver proving they were
 * received. If no ACK packet is received it will keep resending the packets.
 */
public class sender {

    static BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    private static int windowSize = 3;
    private static int timeout = 8000; // in milliseconds
    private static int startingNumber = 0;
    private static int nextSeqNum = 0;
    private static int errorRate = 0;
    private static int randomNumber = 999;
    private static int modulo = 64;
    private static int currentArrayPosition = 0;
    static boolean trim = false;
    static boolean announceWindow = true;

    // use a array to keep track of sent but unACKed packets
    private static packet[] unAckedArray = new packet[modulo];

    // mutual exclusive lock
    private static Object lock = new Object();

    // text files to keep track of acks and sequence numbers
    private static FileOutputStream sequenceNumberFile, acknowlegementFile;


    public static void main(String[] args) throws Exception {
        //        if (args.length != 4) {
        //            System.out.println("Please enter 4 arguments!!!");
        //            System.exit(1);
        //        }


        // Bring up the gui menu
        menu();

        // Manually Enter
        InetAddress hostAddress = InetAddress.getLocalHost();
        System.out.println(hostAddress);
        Integer receiverPort = 9875;
        Integer senderPort = 9874;
        
        // Possible files to send
        File transmitFile = new File("FileToSend\\BeerGame.txt");
//          File transmitFile = new File("FileToSend\\BasicTelecom.txt");

        // Output sender info and options
        System.out.println("Sliding Glass Windows Sender Running");
        System.out.println("Window size is: " + windowSize);
        System.out.println("Error rate is: " + errorRate + "%");
        System.out.println("Timeout in milliseconds is: " + timeout);
        System.out.println("- Begin sending.......... " + transmitFile);
        System.out.println("");

        // Get values by arguments to main
        //        InetAddress hostAddress = InetAddress.getByName(args[0]);
        //        Integer receiverPort = Integer.parseInt(args[1]);
        //        Integer senderPort = Integer.parseInt(args[2]);
        //        File transmitFile = new File(args[3]);

        // fileinputstream used to input the specified file
        FileInputStream fis = new FileInputStream(transmitFile);
        sequenceNumberFile = new FileOutputStream("SND-SequenceNumbers.log");
        acknowlegementFile = new FileOutputStream("SND-FileAcknowlegements.log");

        // Create UDP socket for transmission
        DatagramSocket senderSocket = new DatagramSocket(senderPort);

        // create and start two threads send/receive
        createThreads(hostAddress, receiverPort, fis, senderSocket);

        // close the log files
        sequenceNumberFile.close();
        acknowlegementFile.close();
    }
    
    // Create 2 threads to both send and receive on
    private static void createThreads(final InetAddress addr, final int port,
            final FileInputStream fis, final DatagramSocket socket)
                    throws InterruptedException {

        // thread to send packets to receiver
        Thread threadSendPackets = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    packetSend(fis, addr, port, socket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // thread used to receive ACKs sent by receiver
        Thread threadAckMonitor = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    acksMonitor(addr, port, socket);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // start threads
        threadSendPackets.start();
        threadAckMonitor.start();


        // wait for threads to complete
        threadSendPackets.join();
        threadAckMonitor.join();



        // close the sender socket
        socket.close();
        
        // All done
        System.out.println("\nAll sent packets have been Acknowleged, terminating sender... ");
        //System.out.println("Final Outputting AckArray");
        //outputAckArray();
        //System.out.println("Is the unAckedarray empty? " + arrayIsEmpty());
    }

    private static void packetSend(FileInputStream fis, InetAddress target,
            int port, DatagramSocket socket) throws Exception {

        try {
            packet p;

            while (true) {
            	
                byte[] buffer = new byte[500];
                
            	
            	// window is full wait for acks to slide it
                if (nextSeqNum >= startingNumber + windowSize){
                  //System.out.println("Sliding window full, waiting for ACK: " + startingNumber);
                  //System.out.println("NexSeqNum is: " + nextSeqNum + " and starting number + window size " + windowSize + " is: " + (startingNumber + windowSize));
                  //System.out.println("NextSeqNum must be less than start and window size");
                    
                    // TODO sleep in sliding window
                    // Delay for demonstration
                    try{
                        TimeUnit.MILLISECONDS.sleep(2000);
                    }
                    catch (InterruptedException e) {
                        System.out.println(e);
                    }

                    continue;
                }//end wait for ACKs to slide window

                // mutex
                synchronized (lock) {
                    // input file packet by packet, if -1 returned then it is empty
                    int result = fis.read(buffer);
                    
                    // if nothing left (-1), send EOT packet
                    if (result < 0) {
                        p = packet.createEOT(nextSeqNum);
                        DatagramPacket senderPacket = new DatagramPacket(
                                p.getUDPdata(), p.getUDPdata().length, target,
                                port);
                        socket.send(senderPacket);
                        //System.out.println("Debug:EOT " + p.getSeqNum());
                        String timeStampEOT = new SimpleDateFormat("HH.mm.ss").format(new Date());
                        System.out.println("");
                        System.out.println("[" + timeStampEOT + "] Sending last packet EOT: " + p.getSeqno() + " to receiver");
                        System.out.println(" -All packets sent! waiting to receive all Acks...");
                        announceWindow = false;

                        // add the sent packet to the array as unACKed
                        unAckedArray[currentArrayPosition] = p;
                        currentArrayPosition = ++currentArrayPosition % modulo;

                        // write to log file
                        sequenceNumberFile.write((Integer.toString(nextSeqNum) + '\n')
                                .getBytes());

                        // close fileinputstream
                        fis.close();
                        break;
                    }
                    else {
                    	//Used to trim empty space from the end of the buffer before sending it.
                        String trimmer = new String(buffer);
                        //Only really changes anything on the last packet. Any other packet result should be 500.
                        trimmer = trimmer.substring(0,result);
                        //and then returns it to bytes.
                        buffer = trimmer.getBytes();
                        
                        // Sending normal data packets
                        // Check if RandomErrors
                        if(errorRate > 0){
                            Random randomGenerator = new Random();
                            randomNumber = randomGenerator.nextInt(100);
                            //System.out.println("RandomNumber for packet " + nextSeqNum + " is: " + randomNumber);
                        }
                        
                        // Process if packet lost by sender
                        if(randomNumber <= errorRate){
                            // Chance has decided to lose this packet
                            //System.out.println("Going to lose packet in send...");
                            p = new packet(nextSeqNum, nextSeqNum, buffer);
                            @SuppressWarnings("unused")
                            DatagramPacket senderPacket = new DatagramPacket(
                                    p.getUDPdata(), result, target,
                                    port);
                            String timeStampLosePacket = new SimpleDateFormat("HH.mm.ss").format(new Date());
                            System.out.println("");
                            System.out.println(" *[" + timeStampLosePacket + "] Losing packet: " + p.getSeqno() + " sent to receiver!... ");
                            
                            // DONT ACTUALLY SEND THE PACKET
                            //socket.send(senderPacket);

                            
                            // add the sent packet to the array as unACKed
                            unAckedArray[currentArrayPosition] = p;
                            currentArrayPosition = ++currentArrayPosition % modulo;

                            // write to log file
                            sequenceNumberFile.write((Integer.toString(nextSeqNum) + '\n')
                                    .getBytes());

                            // update nextSeqNum
                            nextSeqNum = ++nextSeqNum % modulo;
                        }// end if erroRate > 0
                        else{
                            // Send the packet correctly with no error
                            //System.out.println("Sending the packet without error");

                            p = new packet(nextSeqNum, nextSeqNum, buffer);
                            DatagramPacket senderPacket = new DatagramPacket(
                                    p.getUDPdata(), p.getUDPdata().length, target,
                                    port);
                            socket.send(senderPacket);
                            
                            System.out.println("");
                            String timeStampCorrectSend = new SimpleDateFormat("HH.mm.ss").format(new Date());
                            System.out.println("[" + timeStampCorrectSend + "] Sending packet: " + p.getSeqno());


                            // add the sent packet to the array as unACKed
                            unAckedArray[currentArrayPosition % modulo] = p;
                            currentArrayPosition =  ++currentArrayPosition % modulo;


                            // write to log file
                            sequenceNumberFile.write((Integer.toString(nextSeqNum) + '\n')
                                    .getBytes());


                            // update nextSeqNum
                            nextSeqNum = ++nextSeqNum % modulo;

                        }// end else send correct packet
                    }// end else send a packet
                }// end lock
            }// end while
        }// end try
        catch (SocketException e) {
        }// end catch SocketException
        catch (IOException e) {
        }// end catch IOException
    }// end sendPackets

    private static void acksMonitor(InetAddress target, int port,
            DatagramSocket socket) throws Exception {

        while (true) {

            try {
                //An Ack packet only holds 8 bytes so the buffer holds 8 to receive that amount.
                byte[] buffer = new byte[8];
                // set socket timeout
                socket.setSoTimeout(timeout);
                // set the socket to receive ACKs
                DatagramPacket ACKs = new DatagramPacket(buffer, buffer.length,
                        target, port);
                socket.receive(ACKs);

                packet ACKPackets = packet.parseUDPdata(ACKs.getData());

                // if the packet is EOT just write to log file and exit
                if (ACKPackets.getType() == 2) {
                    acknowlegementFile.write((Integer.toString(ACKPackets.getAckno()) + '\n')
                            .getBytes());
                    // Removing the EOT from the unAckedArray
                    //System.out.println(" -Removing EOT " + ACKPackets.getSeqNum() + " from unAckedArray");                   
                    String timeStamp1 = new SimpleDateFormat("HH.mm.ss").format(new Date());
                    System.out.println(" !![" + timeStamp1 + "]Got EOT from receiver! All packets have been received!");
                    unAckedArray[currentArrayPosition - 1] = null;
                    
                    // end the ACK Monitor
                    break;

                }// end of eot ACK packet
                
                // Announce received packet
                String timeStampReceiveAck = new SimpleDateFormat("HH.mm.ss").format(new Date());
                System.out.println("");
                System.out.println(" ![" + timeStampReceiveAck + "] Received ACK for packet: " + ACKPackets.getAckno() + " from receiver");

                synchronized (lock) {

                    // Logic to make sure that the previous packet has been removed
                    // before sliding the window
                    if(ACKPackets.getAckno() == 0){
                        // For the Ack of packet 0 do this dont check if
                        // previous packet is null, just ack it
                        
                        // write to log file
                        acknowlegementFile.write((Integer.toString(ACKPackets.getAckno()) + '\n')
                                .getBytes());
                        
                        // Remove the packet from the unACKed array
                        //System.out.println(" -Removing packet " + ACKPackets.getAckno() + " from unAckedArray");
                        unAckedArray[ACKPackets.getAckno()] = null;

                        // Advance the starting number
                        System.out.println("Advancing sliding window...");
                        startingNumber = ++startingNumber % modulo;
//                        System.out.println("After remove starting number is: " + startingNumber);

                    }else if(unAckedArray[ACKPackets.getAckno() - 1] != null){
                        // If the last packet before this one was not acked(not null)
                        // resend the previous packet
                        System.out.println("Warning! previous packet was not ACKed, need ACK from receiver for packet: " + (ACKPackets.getAckno() - 1));
                        //outputAckArray();
                            

                    }else if((unAckedArray[ACKPackets.getAckno() - 1] == null)){
                        // The previous packet has been ACKed, go ahead and
                        // remove this one from the unACKED array

                        // write to log file
                        acknowlegementFile.write((Integer.toString(ACKPackets.getAckno()) + '\n')
                                .getBytes());

                        // Remove the packet from the array
                        //System.out.println(" -Removing " + ACKPackets.getAckno() + " from unAckedArray");                        
                        unAckedArray[ACKPackets.getAckno()] = null;
                        //outputAckArray();
                        
                        
                        // Advance the starting number, which is the smallest
                        // number in the sliding window
                        startingNumber = ++startingNumber % modulo;
                        if(announceWindow == true)
                            System.out.println("Advancing sliding window...");
                    }   
                }// end original if
            } catch (SocketTimeoutException ex) {
                // TODO Timeout
                //System.out.println("Timeout happening for " + ex);

                // when ack timeout occures, resend all the packets in the array
                //mutual exclusive
                synchronized (lock) {

                    // See whats in the array
                    //outputAckArray();
                    
                    // Run through the unACKed array sending all the packets inside
                    for (int i = 0; i < modulo; i++) {
                        
                        // Skip over null packets
                        if(unAckedArray[i] == null){
                            //System.out.println(i + " element is null! Timeout processing continuing...");
                            continue;
                        }
                        
                        // Check if packet loss is requested
                        if(errorRate > 0){
                            // Process packet resends with same error rate as initial ones
                            Random randomGenerator = new Random();
                            randomNumber = randomGenerator.nextInt(100);
                            //System.out.println("RandomNumber for packet resend: " + cache.get(i).getSeqNum() + " is: " + randomNumber);
                        }

                        // Check if packet errored in send
                        if(randomNumber <= errorRate){
                            // Drop the resend
                            @SuppressWarnings("unused")
                            DatagramPacket dp = new DatagramPacket(unAckedArray[i]
                                    .getUDPdata(),
                                    unAckedArray[i].getUDPdata().length, target, port);
                            String timeStampBadResend = new SimpleDateFormat("HH.mm.ss").format(new Date());
                            System.out.println("");
                            System.out.println(" *[" + timeStampBadResend + "] Packet Timeout: Dropping resend of: " + unAckedArray[i].getSeqno());

                            // Dont actually send the resend
                            //socket.send(dp);
                            
                            //write to log file
                            sequenceNumberFile.write((Integer
                                    .toString(unAckedArray[i].getSeqno()) + '\n')
                                    .getBytes());

                        }
                        else{
                            // Send the packet correctly
                            DatagramPacket dp = new DatagramPacket(unAckedArray[i]
                                    .getUDPdata(),
                                    unAckedArray[i].getUDPdata().length, target, port);
                            socket.send(dp);

                            //write to log file
                            sequenceNumberFile.write((Integer
                                    .toString(unAckedArray[i].getSeqno()) + '\n')
                                    .getBytes());

                            //System.out.println("Packet Timeout: Resending " + unAckedList.get(i).getSeqNum());
                            System.out.println("");
                            String timeStampGoodResend = new SimpleDateFormat("HH.mm.ss").format(new Date());
                            System.out.println("[" + timeStampGoodResend + "]Packet Timeout: Correctly resending: " + unAckedArray[i].getSeqno());
                        }
                    }
                }
            }

        }
    }// end acksMonitor


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
            case 3:
                setWindowSize();
                break;
            case 4:
                setTimeout();
                break;
            case 5:
                setModulo();
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
        System.out.println("Sender: Make a selection");
        System.out.println("   1 Run Program");
        System.out.println("   2 Set Error Rate");
        System.out.println("   3 Set Window Size");
        System.out.println("   4 Set Packet Timeout");
        System.out.println("  13 Help");
        System.out.println("   0 Exit");
    }

    public static int getErrorRate() {
        return errorRate;
    }

    public static void setErrorRate(){
        errorRate = Integer.parseInt(stringReader("Please enter the rate of errors (e.g. 30)  \n"));
    }

    public static void setWindowSize(){
        windowSize = Integer.parseInt(stringReader("Please enter the size of the sliding glass window (e.g. 10)  \n"));
    }

    public static void setTimeout(){
        timeout = Integer.parseInt(stringReader("Please enter the time in milliseconds that the packets should timeout before being acknowleged (e.g. 3000)  \n"));
    }

    public static void setModulo(){
        modulo = Integer.parseInt(stringReader("Please enter the modulo for packet wraparound (e.g. 32)  \n"));
    }

    public static void outputAckArray(){
        //System.out.println("");
        System.out.println("Outputting the unacked array...");
        System.out.println("The value of currentArrayPosition is: " + currentArrayPosition);
        //for(int i = 0; i < modulo; i++){
        for(int i = 0; i < 16; i++){

            System.out.println("The value of element: " + i + " is: " + unAckedArray[i]);
        }
    }
    
    // Check if any packets still exist in the unAcked array
    public static boolean arrayIsEmpty(){
        boolean isEmpty = true;
        for(int i = 0; i < modulo; i++){
            if(unAckedArray[i] != null)
                isEmpty = false;
        }

        return isEmpty;
    }// end arrayIsEmpty
}// end class
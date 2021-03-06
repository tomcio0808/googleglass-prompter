package serverapp.server;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.Logger;

import serverapp.frame.MainFrame;

/**
 * @author Tom
 *
 * @created on 6 gru 2015
 *
 */

public class DiscoveryThread implements Runnable {

	private static final Logger log = Logger.getLogger(DiscoveryThread.class.getName());
	
	boolean broadcast = true;
	boolean connected = false;
	
	DatagramSocket broadcastSocket;
	Socket clientSocket;
	ServerSocket serverSocket;
	int port = 8888;
	
	FirewallException firewall;
	
	InputStream socketIn;
	OutputStream socketOut;
	
	ObjectInputStream inStream;
	ObjectOutputStream outStream;
	
	MainFrame mainFrame;
	
	@Override
	public void run() {
		broadcast = true;
		try {
			//creating socket for broadcast connection
			broadcastSocket = new DatagramSocket(port, LocalHost.getInstance().getHostAddress());
			log.info("Server listening on: " + LocalHost.getInstance().getHostAddress() + ", port: " + port);
			broadcastSocket.setBroadcast(true);
			
			while(broadcast){
				log.info("Server ready to receive broadcast packets");
				
				//receiving packet
				byte[] recvBuf = new byte[150000];
				DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
				broadcastSocket.receive(packet);
			
				//Packet received
				log.info("Discovery received from: "+ packet.getAddress().getHostAddress());
				
				//Check packet
				String message = new String(packet.getData()).trim();
				
				if(message.equals("DISCOVER_REQUEST")){
					byte[] sendData = "DISCOVER_RESPONSE".getBytes();
				
					//respond
					DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, packet.getAddress(), packet.getPort());
					broadcastSocket.send(sendPacket);
					
					log.info("Response send to host: "+sendPacket.getAddress().getHostAddress());	
					//close broadcast socket
					broadcastSocket.close();
					
					//create new server - client socket
					serverSocket = new ServerSocket(8888);
					clientSocket = serverSocket.accept();
					connected = true;
					broadcast = false;
					
					log.info("Server connected to client");
					mainFrame.changeLabel("Connected");
					
					socketIn = clientSocket.getInputStream();
					socketOut = clientSocket.getOutputStream();
				
					inStream = new ObjectInputStream(socketIn);
					outStream = new ObjectOutputStream(socketOut);

					//key emulation for presentation movement
					Robot robot = new Robot();
					
					if(inStream != null){
						Thread listen = new Thread(){
							@Override
							public void run() {
								super.run();
								String commandReceived;
								while(connected){
									try {
										commandReceived = inStream.readUTF();
										if(commandReceived.equalsIgnoreCase("start")){
											robot.keyPress(KeyEvent.VK_F5);
											robot.keyRelease(KeyEvent.VK_F5);
										} else if(commandReceived.equalsIgnoreCase("next")) {
											robot.keyPress(KeyEvent.VK_RIGHT);
											robot.keyRelease(KeyEvent.VK_RIGHT);
										} else if(commandReceived.equalsIgnoreCase("previous")){
											robot.keyPress(KeyEvent.VK_LEFT);
											robot.keyRelease(KeyEvent.VK_LEFT);
										} else if(commandReceived.equalsIgnoreCase("exit")){
											robot.keyPress(KeyEvent.VK_ESCAPE);
											robot.keyRelease(KeyEvent.VK_ESCAPE);
										}
									} catch (IOException e) {
										// TODO Auto-generated catch block
										connected = false;
										log.error("Connection lost, restart server");
									}				
								}
								if(!connected){
									mainFrame.stopServer();
								}
							}
						};
						listen.start();
					}
				}			
			}
			if(!broadcast){
				broadcastSocket.close();
			}
		} catch (Exception e) {
			if(!broadcast)
				log.info("Server info: " + e.getMessage());
			else
				log.error("Server error: " + e.getMessage());
		}
		
	}
	
	public void getFrame(MainFrame mf){
		this.mainFrame = mf;
	}
	
	public void sendText(String[] str){
		if(isConnected()){
			try {
				outStream.writeObject(str);
				outStream.flush();
			} catch (IOException e) {
				log.info(e.getMessage());
			}
		}
	}
	
	public void sendFont(boolean[] font){
		if(isConnected()){
			try {
				log.info("Font change send to glass");
				outStream.writeObject(font);
				outStream.flush();
			} catch (IOException e) {
				log.info(e.getMessage());
			}
		}
	}
	
	public void sendChrono(Boolean font){
		String message = "Chronometer is " + (font ? "enabled" : "disabled");
		if(isConnected()){
			try {			
				log.info(message);
				outStream.writeObject(font);
				outStream.flush();
			} catch (IOException e) {
				log.info(e.getMessage());
			}
		}
	}
	
	public boolean isConnected(){
		if(outStream !=null && connected){
			return true;
		} else {
			return false;
		}
	}
	
	public static DiscoveryThread getInstance(){
		return DiscoveryThreadHolder.INSTANCE;
	}
	
	public void stopListening() {
		if(broadcastSocket != null){
			broadcastSocket.close();
		}
		
		if(serverSocket != null){
			try {
				connected = false;
				serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if(clientSocket != null){
			try {
				connected = false;
				clientSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		broadcast = false;
	}
	
	private static class DiscoveryThreadHolder{
		private static final DiscoveryThread INSTANCE = new DiscoveryThread();
	}

}

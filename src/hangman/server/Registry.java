/**
 * @author Marco Ceccotti
*/

package hangman.server;

import hangman.client.IRemoteClient;
import hangman.utils.Message;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Registry extends UnicastRemoteObject implements IRemoteServer
{
	/* configuration object */
	private static JSONObject config_obj;
	/* maximum number of open matches */
	public static int max_matches;
	/* object representing the JSON accounts */
	private JSONObject accounts;
	/* list of connected clients */
	public static HashMap<String, ClientInfo> users;

	/* generated serial ID */
	private static final long serialVersionUID = -2148039017274476724L;
	/* maximum number of users */
	private static final int MAX_USERS = 32;
	/* mutual exclusion objects */
	private static final ReentrantLock LOGIN = new ReentrantLock(), REGISTER = new ReentrantLock();

	public Registry() throws FileNotFoundException, IOException, ParseException
	{
		users = new HashMap<String, ClientInfo>( MAX_USERS );

		// load the accounts
		accounts = (JSONObject) new JSONParser().parse( new FileReader( "./accounts.json" ) );
	}

	public static void main( final String argv[] ) throws FileNotFoundException, ParseException, IOException
	{
		if(argv.length > 0){
			System.out.println( "INVALID NUMBER OF ARGUMENTS" );
			System.out.println( "USAGE: java -jar DistributedHangmanServer" );
			return;
		}

		// load the configurations
		config_obj = (JSONObject) new JSONParser().parse( new FileReader( "./server_config.json" ) );

		System.setProperty( "java.rmi.server.hostname", InetAddress.getLocalHost().getHostAddress() );
		Registry objServer = new Registry();
		java.rmi.registry.Registry reg = LocateRegistry.createRegistry( Integer.parseInt( (String) config_obj.get( "RMI port" ) ) );
		reg.rebind( (String) config_obj.get( "Name" ), objServer );

		max_matches = Integer.parseInt( (String) config_obj.get( "Max Matches" ) );
		Worker.init();

		ServerSocket socket = null;
		Executor thread_pool = Executors.newFixedThreadPool( MAX_USERS );

		try{
			socket = new ServerSocket( Integer.parseInt( (String) config_obj.get( "TCP port" ) ) );
		}catch( IOException e ){
			e.printStackTrace();
			return;
		}

		System.out.println( "[MAIN]: SERVER READY" );

		try{
			while(true){
				// run a new execution of a server worker
				thread_pool.execute( new Worker( socket.accept() ) );
			}
		}catch( Exception e ){
			e.printStackTrace();
		}

		try{
			socket.close();
		}catch( IOException e ){
			e.printStackTrace();
		}

		System.out.println( "[MAIN]: SERVER CLOSED" );
	}

	@Override
	public int checkLogin( final String username, final String password, final IRemoteClient callback ) throws RemoteException
	{
		LOGIN.lock();

		if(users.containsKey( username )){
			LOGIN.unlock();
			return Message.ACCOUNT_ALREADY_IN_USE;
		}

		String acc_pwd = (String) accounts.get( username );
		if(acc_pwd == null){
			LOGIN.unlock();
			return Message.ACCOUNT_DOESNT_EXIST;
		}

		if(acc_pwd.equals( password )){
			if(users.size() == MAX_USERS){
				LOGIN.unlock();
				return Message.SERVER_FULL;
			}

			users.put( username, new ClientInfo( callback ) );

			LOGIN.unlock();

			try{
				updateMatches( callback );
			}
			catch( RemoteException e ){
				e.printStackTrace();
				logout( username );
				return Message.SERVER_ERROR;
			}

			System.out.println( "[RMI SERVER]: CLIENT " + username + " IS CONNECTED" );

			return Message.LOGIN_OK;
		}

		LOGIN.unlock();

		return Message.PASSWORD_INCORRECT;
	}

	@Override
	public void logout( final String username )
	{
		LOGIN.lock();

		users.remove( username );

		LOGIN.unlock();

		System.out.println( "[RMI SERVER]: CLIENT " + username + " IS DISCONNECTED" );
	}

	@SuppressWarnings("unchecked")
	@Override
	public int registerAccount( final String username, final String password, final IRemoteClient callback ) throws RemoteException
	{
		REGISTER.lock();

		if(accounts.containsKey( username )){
			REGISTER.unlock();
			return Message.ACCOUNT_ALREADY_REGISTERED;
		}

		accounts.put( username, password );

		if(saveAccounts() == -1){
			REGISTER.unlock();
			accounts.remove( username );
			logout( username );
			return Message.SERVER_ERROR;
		}

		REGISTER.unlock();

		LOGIN.lock();

		users.put( username, new ClientInfo( callback ) );

		LOGIN.unlock();

		try{
			updateMatches( callback );
		}
		catch( RemoteException e ){
			e.printStackTrace();
			logout( username );
			return Message.SERVER_ERROR;
		}

		System.out.println( "[RMI SERVER]: ACCOUNT " + username + " CREATED" );

		return Message.ACCOUNT_REGISTERED;
	}

	@Override
	public int deleteAccount( final String username ) throws RemoteException
	{
		REGISTER.lock();

		accounts.remove( username );

		if(saveAccounts() == -1){
			REGISTER.unlock();
			logout( username );
			return Message.SERVER_ERROR;
		}

		REGISTER.unlock();

		System.out.println( "[RMI SERVER]: ACCOUNT " + username + " DELETED" );

		return Message.ACCOUNT_DELETED;
	}

	/** save the JSON object to the corresponding file
	 * 
	 * @return 0 if everything is ok, -1 if some error occurs
	*/
	private int saveAccounts()
	{
		try{
			PrintWriter file = new PrintWriter( new FileWriter( "./accounts.json" ) );
			file.write( accounts.toJSONString() );
			file.close();
		}catch( IOException e ){
			e.printStackTrace();
			return -1;
		}

		return 0;
	}

	@Override
	public void getMatches( final IRemoteClient remote_client ) throws RemoteException
	{
		updateMatches( remote_client );
	}

	/** update the matches to one or every user of the server
	 * 
	 * @param remote_client  if the value of this parameter is null the update will be sent to every user,
	 * 						 otherwise only to the one specified by the parameter
	*/
	public static void updateMatches( final IRemoteClient remote_client ) throws RemoteException
	{
		String m_string = Worker.getStringMatches();

		if(remote_client != null)
			remote_client.updateMatches( m_string );
		else{
			LOGIN.lock();

			Iterator<String> it = new HashSet<String>( users.keySet() ).iterator();
			String user;
			while(it.hasNext()){
				ClientInfo client = users.get( user = it.next() );
				if(!client.isInMatch()){
					try{
						client.getCallback().updateMatches( m_string );
					}
					catch( RemoteException e ){
						users.remove( user );
					}
				}
			}

			LOGIN.unlock();
		}
	}
}
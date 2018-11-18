/**
 * @author Marco Ceccotti
*/

package hangman.utils;

import hangman.server.Registry;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Match
{
	/* max number of users */
	private int max_users;
	/* current number of users */
	private int current_users = 0;
	/* master of the match */
	private String master;
	/* list of sockets' output interfaces */
	private ArrayList<ObjectOutputStream> outs;
	/* master output interface */
	private ObjectOutputStream master_out;
	/* own index */
	private int index;
	/* indicates where the search of the next free position must start */
	private static int next_index = -1;
	/* mutual exclusion object */
	private final ReentrantLock MATCH = new ReentrantLock();
	/* determines if the match is closed */
	private boolean closed = false;
	/* list of indexes */
	private static boolean indexes_busy[];
	/* list of ports */
	private static ArrayList<Integer> ports_list;
	/* list of multicast addresses and keys */
	private static ArrayList<String> multicast_addresses, keys;
	
	/* mutual exclusion object to obtain the array index */
	private static final ReentrantLock INDEX_MUTEX = new ReentrantLock();

	public Match( int max_users, String master, ObjectOutputStream out )
	{
		outs = new ArrayList<ObjectOutputStream>( max_users );
		master_out = out;

		index = getIndex();

		this.max_users = max_users;
		this.master = master;
	}

	/** initialize the internal structures */
	public static void init() throws FileNotFoundException, IOException, ParseException
	{
		indexes_busy = new boolean[Registry.max_matches];

		multicast_addresses = new ArrayList<String>( Registry.max_matches );
		ports_list = new ArrayList<Integer>( Registry.max_matches );
		keys = new ArrayList<String>( Registry.max_matches );

		JSONObject obj = (JSONObject) new JSONParser().parse( new FileReader( "./match_settings.json" ) );

		JSONArray multicast_IP = (JSONArray) obj.get( "Multicast IP" );
		Iterator<?> iterator = multicast_IP.iterator();
		while(iterator.hasNext())
			multicast_addresses.add( (String) iterator.next() );

		JSONArray ports = (JSONArray) obj.get( "Ports" );
		iterator = ports.iterator();
		while(iterator.hasNext())
			ports_list.add( Integer.parseInt( (String) iterator.next() ) );

		JSONArray keys_list = (JSONArray) obj.get( "Keys" );
		iterator = keys_list.iterator();
		while(iterator.hasNext())
			keys.add( (String) iterator.next() );
	}

	/** obtain the next free index
	 * 
	 * @return the first free index
	*/
	private int getIndex()
	{
		int index = 0;
		int length = indexes_busy.length;

		INDEX_MUTEX.lock();

		for(int i = 0; i < length; i++){
			next_index = (next_index + 1) % length;

			if(indexes_busy[next_index] == false){
				indexes_busy[index = next_index] = true;
				break;
			}
		}

		INDEX_MUTEX.unlock();

		return index;
	}

	/** realease the acquired index */
	private void releaseIndex()
	{
		INDEX_MUTEX.lock();

		indexes_busy[index] = false;
		next_index = index - 1;

		INDEX_MUTEX.unlock();
	}

	/** return the maximum number of users */
	public int getMaxUsers()
	{
		return max_users;
	}

	/** return the current number of users */
	public int getCurrentUsers()
	{
		return current_users;
	}

	/** return the master of the match */
	public String getMaster()
	{
		return master;
	}

	/** adds a customer to the match
	 * 
	 * @param out	the socket output interface
	 * 
	 * @return 0 if everything is ok, -2 if the match is closed, -3 if the match is full
	*/
	public int addUser( final ObjectOutputStream out ) throws IOException
	{
		MATCH.lock();

		// this variable is set true when the match is closed
		// if the add request arrives after, the user cannot be added to the match
		if(closed){
			MATCH.unlock();
			return -2;
		}

		if(current_users == max_users){
			MATCH.unlock();
			return -3;
		}

		outs.add( out );

		if(++current_users == max_users)
			// warns the master that the match is started
			master_out.writeObject( createMessage( Message.START_MATCH ) );

		MATCH.unlock();

		return 0;
	}

	/** remove a customer from the match
	 * 
	 * @param out	the socket output interface
	*/
	public void removeUser( final ObjectOutputStream out )
	{
		MATCH.lock();

		outs.remove( out );
		current_users--;

		MATCH.unlock();
	}

	/** warn all the guessers that the match is started */
	public void startMatch()
	{
		JSONObject message = createMessage( Message.START_MATCH );

		MATCH.lock();

		// warns all players that the match is started
		for(int i = 0; i < current_users; i++){
			try{
				outs.get( i ).writeObject( message );
			}catch( Exception e ){}
		}

		closed = true;

		MATCH.unlock();
	}

	/** warn all the guessers that the match is over */
	public void closeMatch()
	{
		JSONObject message = createMessage( Message.MATCH_CLOSED );

		MATCH.lock();

		// warns all the players that the match is closed
		for(int i = 0; i < current_users; i++){
			try{
				outs.get( i ).writeObject( message );
			}catch( Exception e ){}
		}

		closed = true;

		MATCH.unlock();

		releaseIndex();
	}

	/** create a new TCP message
	 * 
	 * @param type	type of the message
	*/
	@SuppressWarnings("unchecked")
	private JSONObject createMessage( final char type )
	{
		JSONObject object = new JSONObject();

		object.put( "type", type );

		if(type == Message.START_MATCH){
			// build the object containing all the match informations
			object.put( "address", multicast_addresses.get( index ) );
			object.put( "port", ports_list.get( index ) );
			object.put( "key", keys.get( index ) );
		}

		return object;
	}

	@Override
	public String toString()
	{
		return current_users + "/" + max_users + ", Master: " + master;
	}
}
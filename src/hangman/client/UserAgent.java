/**
 * @author Marco Ceccotti
*/

package hangman.client;

import hangman.server.IRemoteServer;
import hangman.utils.HelpMessage;
import hangman.utils.Message;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class UserAgent extends UnicastRemoteObject implements IRemoteClient
{
	/* object used to "talk" with the server RMI */
	private IRemoteServer remote_obj;
	/* own username and password */
	private String username, password;

	/* maximum number of attempts to guess the account's password */
	private static final int MAX_TRIALS = 3;
	/* input stream scanner */
	private static final Scanner SCAN = new Scanner( System.in );
	/* regexes used to recognize the master and guesser commands */
	private static final String REGEX[] = { "master(?:\\s+\\w*)?", "guesser(?:\\s+\\w*)?", };
	/* regex used to check the correctness of the word to guess */
	private static final String WORD_PATTERN = "[a-zA-Z]*";
	/* generated serial ID */
	private static final long serialVersionUID = -3709614496323392424L;

	public static void main( final String argv[] ) throws FileNotFoundException, IOException, ParseException, NotBoundException
	{
		if(argv.length != 2){
			System.out.println( "INVALID NUMBER OF ARGUMENTS" );
			System.out.println( "USAGE: java -jar DistributedHangmanClient \"username\" \"password\"" );
			return;
		}

		new UserAgent( argv[0], argv[1] );
	}

	public UserAgent( final String username, final String password ) throws FileNotFoundException, IOException, ParseException, NotBoundException
	{
		this.username = username;
		this.password = password;

		String URL = "rmi://";
		JSONObject obj = (JSONObject) new JSONParser().parse( new FileReader( "./client_config.json" ) );
		String serverIP = (String) obj.get( "Server IP" );
		URL = URL + serverIP;
		URL = URL + ":" + (String) obj.get( "RMI Port" );
		URL = URL + "/" + (String) obj.get( "Name" );

		remote_obj = (IRemoteServer) Naming.lookup( URL );

		System.out.println( "<prompt>:: CONNECTED TO THE SERVER: " + serverIP );

		boolean close = false;

		int result = remote_obj.checkLogin( username, password, this );
		switch( result ){
			case( Message.ACCOUNT_DOESNT_EXIST ):
				if(registerAccount() == 0)
					close = true;

				break;

			case( Message.ACCOUNT_ALREADY_IN_USE ):
				System.out.println( "<prompt>:: THE SELECTED ACCOUNT IS ALREADY USED" );
				close = true;
				break;

			case( Message.PASSWORD_INCORRECT ):
				System.out.println( "<prompt>:: THE PASSWORD IS NOT CORRECT; TRY AGAIN" );
				if(guessPassword() != Message.LOGIN_OK)
					close = true;

				break;

			case( Message.SERVER_FULL ):
				System.out.println( "<prompt>:: THE SERVER IS UNABLE TO HANDLE YOUR REQUEST" );
				close = true;
				break;

			case( Message.SERVER_ERROR ):
				System.out.println( "<prompt>:: AN ERROR IS OCCURED INSIDE THE SERVER" );
				System.out.println( "<prompt>:: YOU WILL BE DISCONNECTED..." );
				close = true;
				break;
		}

		if(!close){
			System.out.println( "LOGIN COMPLETED WITH SUCCESSFUL" );
			System.out.println( "<prompt>:: TYPE help FOR COMMANDS LIST" );

			try{
				matchMaking( serverIP, Integer.parseInt( (String) (obj.get( "TCP Port" ) ) ) );
			}catch( IOException e ){
				System.out.println( "<prompt>:: THE SERVER IS NOT REACHABLE" );
				System.out.println( "<prompt>:: YOU WILL BE DISCONNECTED..." );
			}
			catch( Exception ex ){
				ex.printStackTrace();
				System.out.println( "<prompt>:: AN INTERNAL ERROR IS OCCURED" );
				System.out.println( "<prompt>:: YOU WILL BE DISCONNECTED..." );
			}
		}

		SCAN.close();
		System.out.println( "<prompt>:: BYE" );
	}

	/** try to guess the password
	 * 
	 * @return Message.LOGIN_OK if the password is guessed, Message.PASSWORD_INCORRECT if the attempts are over,
	 * 		   Message.SERVER_ERROR if some error occurs
	*/
	private int guessPassword() throws RemoteException
	{
		int result;
		int trials = 1;

		while(trials <= MAX_TRIALS){
			System.out.print( "<prompt>:: PASSWORD (" + trials + " / " + MAX_TRIALS + "): " );

			result = remote_obj.checkLogin( username, SCAN.nextLine(), this );
			if(result == Message.LOGIN_OK){
				System.out.println( "<prompt>:: LOGIN COMPLETED WITH SUCCESSFUL." );
				return Message.LOGIN_OK;
			}
			else{
				if(result == Message.SERVER_ERROR){
					System.out.println( "<prompt>:: AN ERROR IS OCCURED INSIDE THE SERVER" );
					System.out.println( "<prompt>:: YOU WILL BE DISCONNECTED..." );
					return result;
				}

				System.out.println( "<prompt>:: THE PASSWORD IS NOT CORRECT; TRY AGAIN" );
				trials++;
			}
		}

		System.out.println( "<prompt>:: YOU HAVE FINISHED THE ATTEMPTS" );
		System.out.println( "<prompt>:: YOU WILL BE DISCONNECTED..." );

		return Message.PASSWORD_INCORRECT;
	}

	/** check if the user want to register the selected account
	 * 
	 * @return 1 if the account is registered, 0 otherwise
	*/
	private int registerAccount() throws RemoteException
	{
		System.out.print( "<prompt>:: THE SELECTED ACCOUNT DOESN'T EXIST. DO YOU WANT TO USE IT TO CREATE A NEW ONE? (Y or N): " );

		if(SCAN.nextLine().equalsIgnoreCase( "y" )){
			int result = remote_obj.registerAccount( username, password, this );
			if(result == Message.ACCOUNT_ALREADY_REGISTERED) // due to a race condition
				System.out.println( "<prompt>:: THE SELECTED ACCOUNT HAS BEEN ALREADY REGISTERED. TRY WITH ANOTHER ONE" );
			else{
				if(result == Message.SERVER_ERROR)
					System.out.println( "<prompt>:: AN ERROR IS OCCURED IN THE SERVER" );
				else
					System.out.println( "<prompt>:: ACCOUNT CREATED WITH SUCCESSFUL" );

				return 1;
			}
		}

		return 0;
	}

	/** phase where the user selects the guesser or the master side (or even logout)
	 * 
	 * @param serverIP	the server IP address
	 * @param port		TCP port connection
	*/
	private void matchMaking( final String serverIP, final int port ) throws UnknownHostException, IOException
	{
		Player player = null;

		System.out.print( "<prompt>:: " );

		while(true){
			String input = SCAN.nextLine();

			if(player != null && player.isInGame()){
				player.inputManagement( SCAN, input );
				System.out.print( "<prompt>:: " );
				continue;
			}

			String command = input.toLowerCase();

			if(command.equals( "exit" )){
				System.out.print( "DO YOU REALLY WANT TO LOGOUT? (Y or N): " );
				if(SCAN.nextLine().equalsIgnoreCase( "y" )){
					try{ remote_obj.logout( username ); }
					catch( RemoteException e ){}

					System.out.println( "<prompt>:: YOU WILL BE DISCONNECTED..." );
				}

				break;
			}
			else if(command.matches( REGEX[0] )){ // master
				if(command.endsWith( " " ) || command.length() <= 7)
					System.out.println( "<prompt>:: COMMAND ERROR: YOU MUST SPECIFY THE NUMBER OF GUESSERS" );
				else{
					try{
						int customers = Integer.parseInt( command.substring( command.lastIndexOf( ' ' ) + 1 ) );
						if(customers <= 0)
							System.out.println( "<prompt>:: YOU MUST TYPE ONLY NUMBERS GREATER THAN 0" );
						else{
							// ask the word to guess
							System.out.print( "<prompt>:: PLEASE SELECT THE WORD TO GUESS: " );
							String word;
							while(true){
								if((word = SCAN.nextLine()).length() > 0){								
									// checks the correctness
									if(!word.matches( WORD_PATTERN ))
										System.out.println( "<prompt>:: INVALID WORD. IT MUST CONTAINS ONLY CHARACTERS IN THE RANGE a-z or A-Z" );
									else
										break;
								}

								System.out.print( "<prompt>:: PLEASE SELECT THE WORD TO GUESS: " );
							}

							Socket socket = new Socket( serverIP, port );

							player = new Master( customers, socket, username, word );
							player.start();
						}
					}
					catch( NumberFormatException e ){
						System.out.println( "<prompt>:: INVALID NUMBER OF GUESSERS" );
					}
				}
			}
			else if(command.matches( REGEX[1] )){ // guesser
				if(command.endsWith( " " ) || command.length() <= 8)
					System.out.println( "<prompt>:: COMMAND ERROR: YOU MUST SPECIFY THE MASTER USERNAME" );
				else{
					Socket socket = new Socket( serverIP, port );

					player = new Guesser( socket, command.substring( command.lastIndexOf( ' ' ) + 1 ), username );
					player.start();
				}
			}
			else if(command.equals( "list matches" )){
				remote_obj.getMatches( this );
				continue;
			}
			else if(command.equals( "delete account" )){
				System.out.print( "<prompt>:: DO YOU REALLY WANT TO DELETE THE ACCOUNT? (Y or N): " );
				if((command = SCAN.nextLine()).equalsIgnoreCase( "y" )){
					try{
						if(remote_obj.deleteAccount( username ) == Message.SERVER_ERROR)
							System.out.println( "<prompt>:: AN ERROR IS OCCURED IN THE SERVER" );
						else
							remote_obj.logout( username );
					}
					catch( RemoteException e ){}

					System.out.println( "<prompt>:: YOU WILL BE DISCONNECTED..." );
					break;
				}
			}
			else if(command.equals( "help" ))
				HelpMessage.print( HelpMessage.MATCH_MAKING );
			else{
				if(!command.equals( "" ))
					System.out.println( "<prompt>:: UNKNOWN COMMAND \"" + input + "\". TYPE help FOR COMMANDS LIST" );
			}

			System.out.print( "<prompt>:: " );
		}
	}

	@Override
	public void updateMatches( final String matches ) throws RemoteException
	{
		System.out.println( "<prompt>:: LOADING THE LIST OF MATCHES..." );
		System.out.println( matches );

		System.out.print( "<prompt>:: " );
	}
}
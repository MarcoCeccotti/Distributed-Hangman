/**
 * @author Marco Ceccotti
*/

package hangman.utils;

public class HelpMessage
{
	/** current state of the application */
	public static final int GUESSER = 0, MASTER = 1, MATCH_MAKING = 2, GUESSER_GAME = 3, MASTER_GAME = 4;

	/** print the help message
	 * 
	 * @param state  current state
	*/
	public static void print( final int state )
	{
		switch( state ){
			case( MATCH_MAKING ):
				System.out.println( "<prompt>:: list of commands:" );
				System.out.println( "<prompt>::  guesser \"username\" -> join the match specified by the username" );
				System.out.println( "<prompt>::  master K           -> create a new match specifying the number of customers" );
				System.out.println( "<prompt>::  delete account     -> delete the account used to log into the server" );
				System.out.println( "<prompt>::  list matches       -> list all the open matches" );
				System.out.println( "<prompt>::  exit               -> execute the logout from the server" );
				break;

			case( GUESSER ):
				System.out.println( "<prompt>:: list of commands:" );
				System.out.println( "<prompt>::  exit -> leave the group" );
				System.out.print( "<prompt>:: " );
				break;

			case( MASTER ):
				System.out.println( "<prompt>:: list of commands:" );
				System.out.println( "<prompt>::  exit -> close the group" );
				System.out.print( "<prompt>:: " );
				break;

			case( GUESSER_GAME ):
				System.out.println( " list of commands:" );
				System.out.println( "<prompt>::  a - z -> send the choosed letter" );
				System.out.println( "<prompt>::  exit  -> leave the match" );
				System.out.print( "<prompt>:: " );
				break;

			case( MASTER_GAME ):
				System.out.println( " list of commands:" );
				System.out.println( "<prompt>::  exit  -> close the match" );
				System.out.print( "<prompt>:: " );
				break;
		}
	}
}
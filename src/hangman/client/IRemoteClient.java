/**
 * @author Marco Ceccotti
*/

package hangman.client;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IRemoteClient extends Remote
{
	/** update the list of matches
	 * 
	 * @param matches	string representing the current state of the matches
	*/
	public void updateMatches( final String matches ) throws RemoteException;
}
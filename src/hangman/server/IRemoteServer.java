/**
 * @author Marco Ceccotti
*/

package hangman.server;

import hangman.client.IRemoteClient;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IRemoteServer extends Remote
{
	/** check if the login is correct
	 * 
	 * @param username		client username
	 * @param password		client password
	 * @param callback		object used to communicate with the client via RMI callback
	 * 
	 * @return 0 if the login is not correct, a message code instead; -1 if some error occur
	*/
	public int checkLogin( final String username, final String password, final IRemoteClient callback ) throws RemoteException;

	/** disconnect an account
	 * 
	 * @param username		client username
	*/
	public void logout( final String username ) throws RemoteException;

	/** register a new account
	 * 
	 * @param username		client username
	 * @param password		client password
	 * @param callback		object used to communicate with the client via RMI callback
	 * 
	 * @return Message.ACCOUNT_REGISTERED if registered, Message.ACCOUNT_ALREADY_REGISTERED otherwise, Message.SERVER_ERROR if some error occur
	*/
	public int registerAccount( final String username, final String password, final IRemoteClient callback ) throws RemoteException;

	/** delete an account
	 * 
	 * @param username		client username
	 * 
	 * @return Message.ACCOUNT_DELETED if is all ok, Message.SERVER_ERROR if some error occur
	*/
	public int deleteAccount( String username ) throws RemoteException;

	/** get the current state of the matches
	 * 
	 * @param remote_client  object used for the RMI callback
	*/
	public void getMatches( IRemoteClient remote_client ) throws RemoteException;
}
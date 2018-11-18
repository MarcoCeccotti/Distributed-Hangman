/**
 * @author Marco Ceccotti
*/

package hangman.server;

import hangman.client.IRemoteClient;

public class ClientInfo
{
	/* the callback object */
	private IRemoteClient callback;
	/* flag used to manage the status of the client */
	private boolean in_match = false;

	public ClientInfo( final IRemoteClient callback )
	{
		this.callback = callback;
	}

	/** modifie the client status
	 * 
	 * @param flag  TRUE if the client is in a match, FALSE otherwise
	*/
	public synchronized void setInMatch( final boolean flag )
	{
		in_match = flag;
	}

	/** return the client status
	 * 
	 * @return TRUE if the client is in a match, FALSE otherwise
	*/
	public synchronized boolean isInMatch()
	{
		return in_match;
	}

	/** return the callback object associated to the client
	 * 
	 * @return the callback object
	*/
	public IRemoteClient getCallback()
	{
		return callback;
	}
}
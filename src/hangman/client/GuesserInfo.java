/**
 * @author Marco Ceccotti
*/

package hangman.client;

/** Used by the Master to manage the communications with the Guessers */
public class GuesserInfo
{
	/* the packet number */
	private int pkt_number;
	/* the number of old messages */
	private int old_duplicated_messages;

	public GuesserInfo()
	{
		pkt_number = 0;
		old_duplicated_messages = 0;
	}

	/** return the current packet number */
	public int getPktNumber()
	{
		return pkt_number;
	}

	/** assing an updated packet number
	 * 
	 * @param _pkt_number	the new packet number
	*/
	public void setPktNumber( int _pkt_number )
	{
		pkt_number = _pkt_number;
		if(old_duplicated_messages > 0)
			resetOldMessages();
	}

	/** return the number of old messages received */
	public int getOldMessages()
	{
		return old_duplicated_messages;
	}

	/** increase the number of old messages*/
	public void increaseOldMessages()
	{
		old_duplicated_messages++;
	}

	/** reset the number of old messages */
	public void resetOldMessages()
	{
		old_duplicated_messages = 0;
	}
}
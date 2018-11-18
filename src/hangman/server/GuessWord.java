/**
 * @author Marco Ceccotti
*/

package hangman.server;

public class GuessWord
{
	/* the word to be guessed */
	private char word_array[];
	/* length of the word */
	private int length;
	/* the partial word */
	private char partial_word[];
	/* length of the partial word */
	private int partial_length = 0;

	public GuessWord( String word )
	{
		word_array = word.toLowerCase().toCharArray();

		length = word.length();
		partial_word = new char[length];
		for(int i = 0; i < length; i++)
			partial_word[i] = '_';
	}

	/** checks and replaces all the possible occurrencies of the input character
	 * 
	 * @param c  the input character
	 * 
	 * @return TRUE if the input letter is founded inside the remaining letters, FALSE otherwise
	*/
	public boolean checkCharacter( final char c )
	{
		boolean founded = false;

		// replace any occurance of the input character in the partial word
		for(int i = 0; i < length; i++){
			if(word_array[i] == c){
				partial_word[i] = c;
				partial_length++;
				founded = true;
			}
		}

		return founded;
	}

	/** checks if the word is guessed
	 * 
	 * @return TRUE if the word is guessed, FALSE otherwise
	*/
	public boolean isGuessed()
	{
		return partial_length == length;
	}

	/** returns the partial word */
	public String getPartialWord()
	{
		return new String( partial_word );
	}

	/** returns the complete word */
	public String getWord()
	{
		return new String( word_array );
	}
}
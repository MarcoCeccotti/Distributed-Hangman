/**
 * @author Marco Ceccotti
*/

package hangman.utils;

public class Message
{
	public static final int
						ACCOUNT_DOESNT_EXIST		=  1,
						ACCOUNT_ALREADY_IN_USE		=  2,
						PASSWORD_INCORRECT			=  3,
						LOGIN_OK					=  4,
						ACCOUNT_REGISTERED			=  5,
						ACCOUNT_ALREADY_REGISTERED	=  6,
						ACCOUNT_DELETED				=  7,
						SERVER_FULL					=  8,
						SERVER_ERROR				=  9;

	public static final char
						DELETE_ACCOUNT				= 'A',
						MASTER						= 'B',
						GUESSER						= 'C',
						EXIT						= 'D',
						HELLO						= 'E',
						START_MATCH					= 'F',
						NO_MORE_MATCH				= 'G',
						MATCH_CREATED				= 'H',
						MATCH_FULL					= 'I',
						ADDED_TO_MATCH				= 'J',
						MATCH_ALREADY_CLOSED		= 'K',
						MASTER_DOESNT_EXIST			= 'L',
						WORD_TO_TELL				= 'M',
						MATCH_CLOSED				= 'N',
						NEW_LETTER					= 'O',
						PARTIAL_RESULT				= 'P',
						TIMEOUT						= 'Q',
						END_OF_TRIALS				= 'R',
						GUESSER_WIN					= 'S',
						GO_ON						= 'T',
						END_GAME					= 'U';
}
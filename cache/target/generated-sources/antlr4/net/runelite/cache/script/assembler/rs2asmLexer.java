// Generated from net/runelite/cache/script/assembler/rs2asm.g4 by ANTLR 4.13.1
package net.runelite.cache.script.assembler;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class rs2asmLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, NEWLINE=5, INT=6, QSTRING=7, IDENTIFIER=8, 
		SYMBOL=9, COMMENT=10, WS=11;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "NEWLINE", "INT", "QSTRING", "IDENTIFIER", 
			"SYMBOL", "COMMENT", "WS"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'.id '", "'.int_arg_count '", "'.obj_arg_count '", "':'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, "NEWLINE", "INT", "QSTRING", "IDENTIFIER", 
			"SYMBOL", "COMMENT", "WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public rs2asmLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "rs2asm.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\u0004\u0000\u000bq\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002\u0001"+
		"\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004"+
		"\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007"+
		"\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0003\u0001\u0003\u0001\u0004\u0004\u0004@\b\u0004\u000b\u0004"+
		"\f\u0004A\u0001\u0005\u0003\u0005E\b\u0005\u0001\u0005\u0004\u0005H\b"+
		"\u0005\u000b\u0005\f\u0005I\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0005\u0006P\b\u0006\n\u0006\f\u0006S\t\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0007\u0004\u0007X\b\u0007\u000b\u0007\f\u0007Y\u0001\b\u0001"+
		"\b\u0004\b^\b\b\u000b\b\f\b_\u0001\t\u0001\t\u0005\td\b\t\n\t\f\tg\t\t"+
		"\u0001\t\u0001\t\u0001\n\u0004\nl\b\n\u000b\n\f\nm\u0001\n\u0001\n\u0000"+
		"\u0000\u000b\u0001\u0001\u0003\u0002\u0005\u0003\u0007\u0004\t\u0005\u000b"+
		"\u0006\r\u0007\u000f\b\u0011\t\u0013\n\u0015\u000b\u0001\u0000\u0007\u0002"+
		"\u0000\n\n\r\r\u0001\u000009\u0004\u0000\n\n\r\r\"\"\\\\\u0002\u0000\""+
		"\"\\\\\u0004\u000009AZ__az\u0004\u00000:AZ__az\u0002\u0000\t\t  y\u0000"+
		"\u0001\u0001\u0000\u0000\u0000\u0000\u0003\u0001\u0000\u0000\u0000\u0000"+
		"\u0005\u0001\u0000\u0000\u0000\u0000\u0007\u0001\u0000\u0000\u0000\u0000"+
		"\t\u0001\u0000\u0000\u0000\u0000\u000b\u0001\u0000\u0000\u0000\u0000\r"+
		"\u0001\u0000\u0000\u0000\u0000\u000f\u0001\u0000\u0000\u0000\u0000\u0011"+
		"\u0001\u0000\u0000\u0000\u0000\u0013\u0001\u0000\u0000\u0000\u0000\u0015"+
		"\u0001\u0000\u0000\u0000\u0001\u0017\u0001\u0000\u0000\u0000\u0003\u001c"+
		"\u0001\u0000\u0000\u0000\u0005,\u0001\u0000\u0000\u0000\u0007<\u0001\u0000"+
		"\u0000\u0000\t?\u0001\u0000\u0000\u0000\u000bD\u0001\u0000\u0000\u0000"+
		"\rK\u0001\u0000\u0000\u0000\u000fW\u0001\u0000\u0000\u0000\u0011[\u0001"+
		"\u0000\u0000\u0000\u0013a\u0001\u0000\u0000\u0000\u0015k\u0001\u0000\u0000"+
		"\u0000\u0017\u0018\u0005.\u0000\u0000\u0018\u0019\u0005i\u0000\u0000\u0019"+
		"\u001a\u0005d\u0000\u0000\u001a\u001b\u0005 \u0000\u0000\u001b\u0002\u0001"+
		"\u0000\u0000\u0000\u001c\u001d\u0005.\u0000\u0000\u001d\u001e\u0005i\u0000"+
		"\u0000\u001e\u001f\u0005n\u0000\u0000\u001f \u0005t\u0000\u0000 !\u0005"+
		"_\u0000\u0000!\"\u0005a\u0000\u0000\"#\u0005r\u0000\u0000#$\u0005g\u0000"+
		"\u0000$%\u0005_\u0000\u0000%&\u0005c\u0000\u0000&\'\u0005o\u0000\u0000"+
		"\'(\u0005u\u0000\u0000()\u0005n\u0000\u0000)*\u0005t\u0000\u0000*+\u0005"+
		" \u0000\u0000+\u0004\u0001\u0000\u0000\u0000,-\u0005.\u0000\u0000-.\u0005"+
		"o\u0000\u0000./\u0005b\u0000\u0000/0\u0005j\u0000\u000001\u0005_\u0000"+
		"\u000012\u0005a\u0000\u000023\u0005r\u0000\u000034\u0005g\u0000\u0000"+
		"45\u0005_\u0000\u000056\u0005c\u0000\u000067\u0005o\u0000\u000078\u0005"+
		"u\u0000\u000089\u0005n\u0000\u00009:\u0005t\u0000\u0000:;\u0005 \u0000"+
		"\u0000;\u0006\u0001\u0000\u0000\u0000<=\u0005:\u0000\u0000=\b\u0001\u0000"+
		"\u0000\u0000>@\u0007\u0000\u0000\u0000?>\u0001\u0000\u0000\u0000@A\u0001"+
		"\u0000\u0000\u0000A?\u0001\u0000\u0000\u0000AB\u0001\u0000\u0000\u0000"+
		"B\n\u0001\u0000\u0000\u0000CE\u0005-\u0000\u0000DC\u0001\u0000\u0000\u0000"+
		"DE\u0001\u0000\u0000\u0000EG\u0001\u0000\u0000\u0000FH\u0007\u0001\u0000"+
		"\u0000GF\u0001\u0000\u0000\u0000HI\u0001\u0000\u0000\u0000IG\u0001\u0000"+
		"\u0000\u0000IJ\u0001\u0000\u0000\u0000J\f\u0001\u0000\u0000\u0000KQ\u0005"+
		"\"\u0000\u0000LP\b\u0002\u0000\u0000MN\u0005\\\u0000\u0000NP\u0007\u0003"+
		"\u0000\u0000OL\u0001\u0000\u0000\u0000OM\u0001\u0000\u0000\u0000PS\u0001"+
		"\u0000\u0000\u0000QO\u0001\u0000\u0000\u0000QR\u0001\u0000\u0000\u0000"+
		"RT\u0001\u0000\u0000\u0000SQ\u0001\u0000\u0000\u0000TU\u0005\"\u0000\u0000"+
		"U\u000e\u0001\u0000\u0000\u0000VX\u0007\u0004\u0000\u0000WV\u0001\u0000"+
		"\u0000\u0000XY\u0001\u0000\u0000\u0000YW\u0001\u0000\u0000\u0000YZ\u0001"+
		"\u0000\u0000\u0000Z\u0010\u0001\u0000\u0000\u0000[]\u0005:\u0000\u0000"+
		"\\^\u0007\u0005\u0000\u0000]\\\u0001\u0000\u0000\u0000^_\u0001\u0000\u0000"+
		"\u0000_]\u0001\u0000\u0000\u0000_`\u0001\u0000\u0000\u0000`\u0012\u0001"+
		"\u0000\u0000\u0000ae\u0005;\u0000\u0000bd\b\u0000\u0000\u0000cb\u0001"+
		"\u0000\u0000\u0000dg\u0001\u0000\u0000\u0000ec\u0001\u0000\u0000\u0000"+
		"ef\u0001\u0000\u0000\u0000fh\u0001\u0000\u0000\u0000ge\u0001\u0000\u0000"+
		"\u0000hi\u0006\t\u0000\u0000i\u0014\u0001\u0000\u0000\u0000jl\u0007\u0006"+
		"\u0000\u0000kj\u0001\u0000\u0000\u0000lm\u0001\u0000\u0000\u0000mk\u0001"+
		"\u0000\u0000\u0000mn\u0001\u0000\u0000\u0000no\u0001\u0000\u0000\u0000"+
		"op\u0006\n\u0000\u0000p\u0016\u0001\u0000\u0000\u0000\n\u0000ADIOQY_e"+
		"m\u0001\u0000\u0001\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
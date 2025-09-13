// Generated from net/runelite/cache/script/assembler/rs2asm.g4 by ANTLR 4.13.1
package net.runelite.cache.script.assembler;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class rs2asmParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, NEWLINE=5, INT=6, QSTRING=7, IDENTIFIER=8, 
		SYMBOL=9, COMMENT=10, WS=11;
	public static final int
		RULE_prog = 0, RULE_header = 1, RULE_id = 2, RULE_int_arg_count = 3, RULE_obj_arg_count = 4, 
		RULE_id_value = 5, RULE_int_arg_value = 6, RULE_obj_arg_value = 7, RULE_line = 8, 
		RULE_instruction = 9, RULE_label = 10, RULE_instruction_name = 11, RULE_name_string = 12, 
		RULE_name_opcode = 13, RULE_instruction_operand = 14, RULE_operand_int = 15, 
		RULE_operand_qstring = 16, RULE_operand_label = 17, RULE_operand_symbol = 18, 
		RULE_switch_lookup = 19, RULE_switch_key = 20, RULE_switch_value = 21;
	private static String[] makeRuleNames() {
		return new String[] {
			"prog", "header", "id", "int_arg_count", "obj_arg_count", "id_value", 
			"int_arg_value", "obj_arg_value", "line", "instruction", "label", "instruction_name", 
			"name_string", "name_opcode", "instruction_operand", "operand_int", "operand_qstring", 
			"operand_label", "operand_symbol", "switch_lookup", "switch_key", "switch_value"
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

	@Override
	public String getGrammarFileName() { return "rs2asm.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public rs2asmParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ProgContext extends ParserRuleContext {
		public List<TerminalNode> NEWLINE() { return getTokens(rs2asmParser.NEWLINE); }
		public TerminalNode NEWLINE(int i) {
			return getToken(rs2asmParser.NEWLINE, i);
		}
		public List<HeaderContext> header() {
			return getRuleContexts(HeaderContext.class);
		}
		public HeaderContext header(int i) {
			return getRuleContext(HeaderContext.class,i);
		}
		public List<LineContext> line() {
			return getRuleContexts(LineContext.class);
		}
		public LineContext line(int i) {
			return getRuleContext(LineContext.class,i);
		}
		public ProgContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_prog; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterProg(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitProg(this);
		}
	}

	public final ProgContext prog() throws RecognitionException {
		ProgContext _localctx = new ProgContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_prog);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(47);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NEWLINE) {
				{
				{
				setState(44);
				match(NEWLINE);
				}
				}
				setState(49);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(58);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 14L) != 0)) {
				{
				{
				setState(50);
				header();
				setState(52); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(51);
					match(NEWLINE);
					}
					}
					setState(54); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==NEWLINE );
				}
				}
				setState(60);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(67); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(61);
				line();
				setState(63); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(62);
					match(NEWLINE);
					}
					}
					setState(65); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==NEWLINE );
				}
				}
				setState(69); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==INT || _la==IDENTIFIER );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class HeaderContext extends ParserRuleContext {
		public IdContext id() {
			return getRuleContext(IdContext.class,0);
		}
		public Int_arg_countContext int_arg_count() {
			return getRuleContext(Int_arg_countContext.class,0);
		}
		public Obj_arg_countContext obj_arg_count() {
			return getRuleContext(Obj_arg_countContext.class,0);
		}
		public HeaderContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_header; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterHeader(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitHeader(this);
		}
	}

	public final HeaderContext header() throws RecognitionException {
		HeaderContext _localctx = new HeaderContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_header);
		try {
			setState(74);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__0:
				enterOuterAlt(_localctx, 1);
				{
				setState(71);
				id();
				}
				break;
			case T__1:
				enterOuterAlt(_localctx, 2);
				{
				setState(72);
				int_arg_count();
				}
				break;
			case T__2:
				enterOuterAlt(_localctx, 3);
				{
				setState(73);
				obj_arg_count();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IdContext extends ParserRuleContext {
		public Id_valueContext id_value() {
			return getRuleContext(Id_valueContext.class,0);
		}
		public IdContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_id; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterId(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitId(this);
		}
	}

	public final IdContext id() throws RecognitionException {
		IdContext _localctx = new IdContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_id);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(76);
			match(T__0);
			setState(77);
			id_value();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Int_arg_countContext extends ParserRuleContext {
		public Int_arg_valueContext int_arg_value() {
			return getRuleContext(Int_arg_valueContext.class,0);
		}
		public Int_arg_countContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_int_arg_count; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterInt_arg_count(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitInt_arg_count(this);
		}
	}

	public final Int_arg_countContext int_arg_count() throws RecognitionException {
		Int_arg_countContext _localctx = new Int_arg_countContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_int_arg_count);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(79);
			match(T__1);
			setState(80);
			int_arg_value();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Obj_arg_countContext extends ParserRuleContext {
		public Obj_arg_valueContext obj_arg_value() {
			return getRuleContext(Obj_arg_valueContext.class,0);
		}
		public Obj_arg_countContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_obj_arg_count; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterObj_arg_count(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitObj_arg_count(this);
		}
	}

	public final Obj_arg_countContext obj_arg_count() throws RecognitionException {
		Obj_arg_countContext _localctx = new Obj_arg_countContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_obj_arg_count);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(82);
			match(T__2);
			setState(83);
			obj_arg_value();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Id_valueContext extends ParserRuleContext {
		public TerminalNode INT() { return getToken(rs2asmParser.INT, 0); }
		public Id_valueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_id_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterId_value(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitId_value(this);
		}
	}

	public final Id_valueContext id_value() throws RecognitionException {
		Id_valueContext _localctx = new Id_valueContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_id_value);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(85);
			match(INT);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Int_arg_valueContext extends ParserRuleContext {
		public TerminalNode INT() { return getToken(rs2asmParser.INT, 0); }
		public Int_arg_valueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_int_arg_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterInt_arg_value(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitInt_arg_value(this);
		}
	}

	public final Int_arg_valueContext int_arg_value() throws RecognitionException {
		Int_arg_valueContext _localctx = new Int_arg_valueContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_int_arg_value);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(87);
			match(INT);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Obj_arg_valueContext extends ParserRuleContext {
		public TerminalNode INT() { return getToken(rs2asmParser.INT, 0); }
		public Obj_arg_valueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_obj_arg_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterObj_arg_value(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitObj_arg_value(this);
		}
	}

	public final Obj_arg_valueContext obj_arg_value() throws RecognitionException {
		Obj_arg_valueContext _localctx = new Obj_arg_valueContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_obj_arg_value);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(89);
			match(INT);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LineContext extends ParserRuleContext {
		public InstructionContext instruction() {
			return getRuleContext(InstructionContext.class,0);
		}
		public LabelContext label() {
			return getRuleContext(LabelContext.class,0);
		}
		public Switch_lookupContext switch_lookup() {
			return getRuleContext(Switch_lookupContext.class,0);
		}
		public LineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_line; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterLine(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitLine(this);
		}
	}

	public final LineContext line() throws RecognitionException {
		LineContext _localctx = new LineContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_line);
		try {
			setState(94);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(91);
				instruction();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(92);
				label();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(93);
				switch_lookup();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InstructionContext extends ParserRuleContext {
		public Instruction_nameContext instruction_name() {
			return getRuleContext(Instruction_nameContext.class,0);
		}
		public Instruction_operandContext instruction_operand() {
			return getRuleContext(Instruction_operandContext.class,0);
		}
		public InstructionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_instruction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterInstruction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitInstruction(this);
		}
	}

	public final InstructionContext instruction() throws RecognitionException {
		InstructionContext _localctx = new InstructionContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_instruction);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(96);
			instruction_name();
			setState(97);
			instruction_operand();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LabelContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(rs2asmParser.IDENTIFIER, 0); }
		public LabelContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_label; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterLabel(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitLabel(this);
		}
	}

	public final LabelContext label() throws RecognitionException {
		LabelContext _localctx = new LabelContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_label);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(99);
			match(IDENTIFIER);
			setState(100);
			match(T__3);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Instruction_nameContext extends ParserRuleContext {
		public Name_stringContext name_string() {
			return getRuleContext(Name_stringContext.class,0);
		}
		public Name_opcodeContext name_opcode() {
			return getRuleContext(Name_opcodeContext.class,0);
		}
		public Instruction_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_instruction_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterInstruction_name(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitInstruction_name(this);
		}
	}

	public final Instruction_nameContext instruction_name() throws RecognitionException {
		Instruction_nameContext _localctx = new Instruction_nameContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_instruction_name);
		try {
			setState(104);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IDENTIFIER:
				enterOuterAlt(_localctx, 1);
				{
				setState(102);
				name_string();
				}
				break;
			case INT:
				enterOuterAlt(_localctx, 2);
				{
				setState(103);
				name_opcode();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Name_stringContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(rs2asmParser.IDENTIFIER, 0); }
		public Name_stringContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_name_string; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterName_string(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitName_string(this);
		}
	}

	public final Name_stringContext name_string() throws RecognitionException {
		Name_stringContext _localctx = new Name_stringContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_name_string);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(106);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Name_opcodeContext extends ParserRuleContext {
		public TerminalNode INT() { return getToken(rs2asmParser.INT, 0); }
		public Name_opcodeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_name_opcode; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterName_opcode(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitName_opcode(this);
		}
	}

	public final Name_opcodeContext name_opcode() throws RecognitionException {
		Name_opcodeContext _localctx = new Name_opcodeContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_name_opcode);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(108);
			match(INT);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Instruction_operandContext extends ParserRuleContext {
		public Operand_intContext operand_int() {
			return getRuleContext(Operand_intContext.class,0);
		}
		public Operand_qstringContext operand_qstring() {
			return getRuleContext(Operand_qstringContext.class,0);
		}
		public Operand_labelContext operand_label() {
			return getRuleContext(Operand_labelContext.class,0);
		}
		public Operand_symbolContext operand_symbol() {
			return getRuleContext(Operand_symbolContext.class,0);
		}
		public Instruction_operandContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_instruction_operand; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterInstruction_operand(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitInstruction_operand(this);
		}
	}

	public final Instruction_operandContext instruction_operand() throws RecognitionException {
		Instruction_operandContext _localctx = new Instruction_operandContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_instruction_operand);
		try {
			setState(115);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INT:
				enterOuterAlt(_localctx, 1);
				{
				setState(110);
				operand_int();
				}
				break;
			case QSTRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(111);
				operand_qstring();
				}
				break;
			case IDENTIFIER:
				enterOuterAlt(_localctx, 3);
				{
				setState(112);
				operand_label();
				}
				break;
			case SYMBOL:
				enterOuterAlt(_localctx, 4);
				{
				setState(113);
				operand_symbol();
				}
				break;
			case NEWLINE:
				enterOuterAlt(_localctx, 5);
				{
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Operand_intContext extends ParserRuleContext {
		public TerminalNode INT() { return getToken(rs2asmParser.INT, 0); }
		public Operand_intContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_operand_int; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterOperand_int(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitOperand_int(this);
		}
	}

	public final Operand_intContext operand_int() throws RecognitionException {
		Operand_intContext _localctx = new Operand_intContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_operand_int);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(117);
			match(INT);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Operand_qstringContext extends ParserRuleContext {
		public TerminalNode QSTRING() { return getToken(rs2asmParser.QSTRING, 0); }
		public Operand_qstringContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_operand_qstring; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterOperand_qstring(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitOperand_qstring(this);
		}
	}

	public final Operand_qstringContext operand_qstring() throws RecognitionException {
		Operand_qstringContext _localctx = new Operand_qstringContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_operand_qstring);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(119);
			match(QSTRING);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Operand_labelContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(rs2asmParser.IDENTIFIER, 0); }
		public Operand_labelContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_operand_label; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterOperand_label(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitOperand_label(this);
		}
	}

	public final Operand_labelContext operand_label() throws RecognitionException {
		Operand_labelContext _localctx = new Operand_labelContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_operand_label);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(121);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Operand_symbolContext extends ParserRuleContext {
		public TerminalNode SYMBOL() { return getToken(rs2asmParser.SYMBOL, 0); }
		public Operand_symbolContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_operand_symbol; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterOperand_symbol(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitOperand_symbol(this);
		}
	}

	public final Operand_symbolContext operand_symbol() throws RecognitionException {
		Operand_symbolContext _localctx = new Operand_symbolContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_operand_symbol);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(123);
			match(SYMBOL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Switch_lookupContext extends ParserRuleContext {
		public Switch_keyContext switch_key() {
			return getRuleContext(Switch_keyContext.class,0);
		}
		public Switch_valueContext switch_value() {
			return getRuleContext(Switch_valueContext.class,0);
		}
		public Switch_lookupContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_switch_lookup; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterSwitch_lookup(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitSwitch_lookup(this);
		}
	}

	public final Switch_lookupContext switch_lookup() throws RecognitionException {
		Switch_lookupContext _localctx = new Switch_lookupContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_switch_lookup);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(125);
			switch_key();
			setState(126);
			match(T__3);
			setState(127);
			switch_value();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Switch_keyContext extends ParserRuleContext {
		public TerminalNode INT() { return getToken(rs2asmParser.INT, 0); }
		public Switch_keyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_switch_key; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterSwitch_key(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitSwitch_key(this);
		}
	}

	public final Switch_keyContext switch_key() throws RecognitionException {
		Switch_keyContext _localctx = new Switch_keyContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_switch_key);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(129);
			match(INT);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Switch_valueContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(rs2asmParser.IDENTIFIER, 0); }
		public Switch_valueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_switch_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).enterSwitch_value(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof rs2asmListener ) ((rs2asmListener)listener).exitSwitch_value(this);
		}
	}

	public final Switch_valueContext switch_value() throws RecognitionException {
		Switch_valueContext _localctx = new Switch_valueContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_switch_value);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(131);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u000b\u0086\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b"+
		"\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007"+
		"\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007"+
		"\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007"+
		"\u0015\u0001\u0000\u0005\u0000.\b\u0000\n\u0000\f\u00001\t\u0000\u0001"+
		"\u0000\u0001\u0000\u0004\u00005\b\u0000\u000b\u0000\f\u00006\u0005\u0000"+
		"9\b\u0000\n\u0000\f\u0000<\t\u0000\u0001\u0000\u0001\u0000\u0004\u0000"+
		"@\b\u0000\u000b\u0000\f\u0000A\u0004\u0000D\b\u0000\u000b\u0000\f\u0000"+
		"E\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001K\b\u0001\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006"+
		"\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001\b\u0003\b_\b\b\u0001\t"+
		"\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0003"+
		"\u000bi\b\u000b\u0001\f\u0001\f\u0001\r\u0001\r\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0003\u000et\b\u000e\u0001\u000f"+
		"\u0001\u000f\u0001\u0010\u0001\u0010\u0001\u0011\u0001\u0011\u0001\u0012"+
		"\u0001\u0012\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0014"+
		"\u0001\u0014\u0001\u0015\u0001\u0015\u0001\u0015\u0000\u0000\u0016\u0000"+
		"\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018\u001a\u001c"+
		"\u001e \"$&(*\u0000\u0000}\u0000/\u0001\u0000\u0000\u0000\u0002J\u0001"+
		"\u0000\u0000\u0000\u0004L\u0001\u0000\u0000\u0000\u0006O\u0001\u0000\u0000"+
		"\u0000\bR\u0001\u0000\u0000\u0000\nU\u0001\u0000\u0000\u0000\fW\u0001"+
		"\u0000\u0000\u0000\u000eY\u0001\u0000\u0000\u0000\u0010^\u0001\u0000\u0000"+
		"\u0000\u0012`\u0001\u0000\u0000\u0000\u0014c\u0001\u0000\u0000\u0000\u0016"+
		"h\u0001\u0000\u0000\u0000\u0018j\u0001\u0000\u0000\u0000\u001al\u0001"+
		"\u0000\u0000\u0000\u001cs\u0001\u0000\u0000\u0000\u001eu\u0001\u0000\u0000"+
		"\u0000 w\u0001\u0000\u0000\u0000\"y\u0001\u0000\u0000\u0000${\u0001\u0000"+
		"\u0000\u0000&}\u0001\u0000\u0000\u0000(\u0081\u0001\u0000\u0000\u0000"+
		"*\u0083\u0001\u0000\u0000\u0000,.\u0005\u0005\u0000\u0000-,\u0001\u0000"+
		"\u0000\u0000.1\u0001\u0000\u0000\u0000/-\u0001\u0000\u0000\u0000/0\u0001"+
		"\u0000\u0000\u00000:\u0001\u0000\u0000\u00001/\u0001\u0000\u0000\u0000"+
		"24\u0003\u0002\u0001\u000035\u0005\u0005\u0000\u000043\u0001\u0000\u0000"+
		"\u000056\u0001\u0000\u0000\u000064\u0001\u0000\u0000\u000067\u0001\u0000"+
		"\u0000\u000079\u0001\u0000\u0000\u000082\u0001\u0000\u0000\u00009<\u0001"+
		"\u0000\u0000\u0000:8\u0001\u0000\u0000\u0000:;\u0001\u0000\u0000\u0000"+
		";C\u0001\u0000\u0000\u0000<:\u0001\u0000\u0000\u0000=?\u0003\u0010\b\u0000"+
		">@\u0005\u0005\u0000\u0000?>\u0001\u0000\u0000\u0000@A\u0001\u0000\u0000"+
		"\u0000A?\u0001\u0000\u0000\u0000AB\u0001\u0000\u0000\u0000BD\u0001\u0000"+
		"\u0000\u0000C=\u0001\u0000\u0000\u0000DE\u0001\u0000\u0000\u0000EC\u0001"+
		"\u0000\u0000\u0000EF\u0001\u0000\u0000\u0000F\u0001\u0001\u0000\u0000"+
		"\u0000GK\u0003\u0004\u0002\u0000HK\u0003\u0006\u0003\u0000IK\u0003\b\u0004"+
		"\u0000JG\u0001\u0000\u0000\u0000JH\u0001\u0000\u0000\u0000JI\u0001\u0000"+
		"\u0000\u0000K\u0003\u0001\u0000\u0000\u0000LM\u0005\u0001\u0000\u0000"+
		"MN\u0003\n\u0005\u0000N\u0005\u0001\u0000\u0000\u0000OP\u0005\u0002\u0000"+
		"\u0000PQ\u0003\f\u0006\u0000Q\u0007\u0001\u0000\u0000\u0000RS\u0005\u0003"+
		"\u0000\u0000ST\u0003\u000e\u0007\u0000T\t\u0001\u0000\u0000\u0000UV\u0005"+
		"\u0006\u0000\u0000V\u000b\u0001\u0000\u0000\u0000WX\u0005\u0006\u0000"+
		"\u0000X\r\u0001\u0000\u0000\u0000YZ\u0005\u0006\u0000\u0000Z\u000f\u0001"+
		"\u0000\u0000\u0000[_\u0003\u0012\t\u0000\\_\u0003\u0014\n\u0000]_\u0003"+
		"&\u0013\u0000^[\u0001\u0000\u0000\u0000^\\\u0001\u0000\u0000\u0000^]\u0001"+
		"\u0000\u0000\u0000_\u0011\u0001\u0000\u0000\u0000`a\u0003\u0016\u000b"+
		"\u0000ab\u0003\u001c\u000e\u0000b\u0013\u0001\u0000\u0000\u0000cd\u0005"+
		"\b\u0000\u0000de\u0005\u0004\u0000\u0000e\u0015\u0001\u0000\u0000\u0000"+
		"fi\u0003\u0018\f\u0000gi\u0003\u001a\r\u0000hf\u0001\u0000\u0000\u0000"+
		"hg\u0001\u0000\u0000\u0000i\u0017\u0001\u0000\u0000\u0000jk\u0005\b\u0000"+
		"\u0000k\u0019\u0001\u0000\u0000\u0000lm\u0005\u0006\u0000\u0000m\u001b"+
		"\u0001\u0000\u0000\u0000nt\u0003\u001e\u000f\u0000ot\u0003 \u0010\u0000"+
		"pt\u0003\"\u0011\u0000qt\u0003$\u0012\u0000rt\u0001\u0000\u0000\u0000"+
		"sn\u0001\u0000\u0000\u0000so\u0001\u0000\u0000\u0000sp\u0001\u0000\u0000"+
		"\u0000sq\u0001\u0000\u0000\u0000sr\u0001\u0000\u0000\u0000t\u001d\u0001"+
		"\u0000\u0000\u0000uv\u0005\u0006\u0000\u0000v\u001f\u0001\u0000\u0000"+
		"\u0000wx\u0005\u0007\u0000\u0000x!\u0001\u0000\u0000\u0000yz\u0005\b\u0000"+
		"\u0000z#\u0001\u0000\u0000\u0000{|\u0005\t\u0000\u0000|%\u0001\u0000\u0000"+
		"\u0000}~\u0003(\u0014\u0000~\u007f\u0005\u0004\u0000\u0000\u007f\u0080"+
		"\u0003*\u0015\u0000\u0080\'\u0001\u0000\u0000\u0000\u0081\u0082\u0005"+
		"\u0006\u0000\u0000\u0082)\u0001\u0000\u0000\u0000\u0083\u0084\u0005\b"+
		"\u0000\u0000\u0084+\u0001\u0000\u0000\u0000\t/6:AEJ^hs";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
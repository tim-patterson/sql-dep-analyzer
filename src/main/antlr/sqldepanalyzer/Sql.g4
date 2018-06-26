grammar Sql;

@header {
 package sqldepanalyzer;
}

file
  : stmt (';' stmt)* ';'? EOF
  ;

stmt
  : create_database_stmt
  | create_table_stmt
  | create_view_stmt
  | set_stmt
  | add_stmt
  | select_stmt
  ;

create_database_stmt
  : CREATE DATABASE (IF NOT EXISTS)? IDENTIFIER
  ;

create_table_stmt
  : CREATE TEMPORARY? EXTERNAL? TABLE (IF NOT EXISTS)? table_identifier
  create_table_field_list?
  create_table_comment_clause?
  create_table_partition_clause?
  create_table_row_format_clause?
  create_table_stored_as_clause?
  create_table_location_clause?
  create_table_tblproperties_clause?
  ;

create_table_comment_clause
  : COMMENT STRING_LITERAL
  ;

create_table_partition_clause
  : PARTITIONED BY create_table_field_list
  ;

create_table_row_format_clause
  : ROW FORMAT SERDE STRING_LITERAL (WITH SERDEPROPERTIES create_table_properties_list )?
  | ROW FORMAT DELIMITED
  | ROW FORMAT DELIMITED FIELDS TERMINATED BY STRING_LITERAL (ESCAPED BY STRING_LITERAL)?
  ;

create_table_stored_as_clause
  : STORED AS IDENTIFIER
  | STORED AS INPUTFORMAT STRING_LITERAL OUTPUTFORMAT STRING_LITERAL
  ;

create_table_location_clause
  : LOCATION STRING_LITERAL
  ;

create_table_tblproperties_clause
  : TBLPROPERTIES create_table_properties_list
  ;

create_table_field_list
  : '(' (field_spec (',' field_spec)*)? ')'
  ;

create_table_properties_list
  : '(' (create_table_property (',' create_table_property)*)? ')'
  ;

create_table_property
  : STRING_LITERAL '=' STRING_LITERAL
  ;

create_view_stmt
  : CREATE VIEW (IF NOT EXISTS)? table_identifier AS select_stmt
  ;

set_stmt
  : SET config_variable '=' anything*
  ;

add_stmt
  : ADD anything*
  ;

field_spec
  : indentifier type
  ;

type
  : IDENTIFIER ('(' NUMERIC_LITERAL ( ',' NUMERIC_LITERAL)?')')?
  | IDENTIFIER '<' type '>' // array
  | IDENTIFIER '<' IDENTIFIER ':' type (',' IDENTIFIER ':' type)* '>' // struct
  | IDENTIFIER '<' IDENTIFIER (',' IDENTIFIER)* '>' // map
  ;

select_stmt
  : select_clause from_clause?
  ;

select_clause
  : SELECT named_expression*
  ;

from_clause
  : FROM table_identifier
  ;

named_expression
  : expression
  | expression AS indentifier
  ;

expression
  : indentifier
  | NUMERIC_LITERAL
  | STRING_LITERAL
  ;

table_identifier
  : indentifier ('.' indentifier)?
  ;

config_variable
  : indentifier ('.' indentifier)*
  ;

indentifier
  : IDENTIFIER
  | keyword
  ;

// Rules needed to make the set statement work

anything
  : keyword
  | operator
  | IDENTIFIER
  | NUMERIC_LITERAL
  | STRING_LITERAL
  | ':'
  ;

keyword
  : ADD
  | AS
  | BY
  | COMMENT
  | CREATE
  | DATABASE
  | DELIMITED
  | ESCAPED
  | EXISTS
  | EXTERNAL
  | FIELDS
  | FORMAT
  | FROM
  | IF
  | INPUTFORMAT
  | LOCATION
  | NOT
  | OUTPUTFORMAT
  | PARTITIONED
  | ROW
  | SERDE
  | SERDEPROPERTIES
  | SELECT
  | SET
  | STORED
  | TABLE
  | TBLPROPERTIES
  | TEMPORARY
  | TERMINATED
  | WITH
  | VIEW
  ;

operator
  : OP_PLUS
  | OP_MINUS
  | OP_MULT
  | OP_DIV
  | OP_GT
  | OP_GTE
  | OP_LT
  | OP_LTE
  | OP_EQ
  | OP_NEQ
  | OP_DOT
  | OP_IDX
  | OP_AND
  | OP_OR
  ;


// Key words
ADD: A D D;
AS: A S;
BY: B Y;
COMMENT: C O M M E N T;
CREATE: C R E A T E;
DATABASE: D A T A B A S E;
DELIMITED: D E L I M I T E D;
ESCAPED: E S C A P E D;
EXISTS: E X I S T S;
EXTERNAL: E X T E R N A L;
FIELDS: F I E L D S;
FORMAT: F O R M A T;
FROM: F R O M;
IF: I F;
INPUTFORMAT: I N P U T F O R M A T;
LOCATION: L O C A T I O N;
NOT: N O T;
OUTPUTFORMAT: O U T P U T F O R M A T;
PARTITIONED: P A R T I T I O N E D;
ROW: R O W;
SERDE: S E R D E;
SERDEPROPERTIES: S E R D E P R O P E R T I E S;
SELECT: S E L E C T;
SET: S E T;
STORED: S T O R E D;
TABLE: T A B L E;
TBLPROPERTIES: T B L P R O P E R T I E S;
TEMPORARY: T E M P O R A R Y;
TERMINATED: T E R M I N A T E D;
WITH: W I T H;
VIEW: V I E W;


OP_PLUS: '+';
OP_MINUS: '-';
OP_MULT: '*';
OP_DIV: '/';
OP_GT: '>';
OP_GTE: '>=';
OP_LT: '<';
OP_LTE: '<=';
OP_EQ: '=' | '==';
OP_NEQ: '!=' | '<>';
OP_DOT: '.';
OP_IDX: '[';
OP_AND: A N D;
OP_OR: O R;

IDENTIFIER
 : [a-zA-Z_] [a-zA-Z_0-9]*
 | '`' ( ~'`' )* '`'
 ;

SINGLE_LINE_COMMENT
 : '--' ~[\r\n]* -> channel(HIDDEN)
 ;

// Literals
NUMERIC_LITERAL
 : '-'? DIGIT+ ( '.' DIGIT* )?
 ;

STRING_LITERAL
 : '\'' (~'\'')* '\''
 | '"' (('\\' .) | ~('\\' | '"'))* '"'
 ;

// Whitespace
SPACES
 : [ \u000B\t\r\n] -> channel(HIDDEN)
 ;

// Fragments
fragment DIGIT : [0-9];

fragment A : [aA];
fragment B : [bB];
fragment C : [cC];
fragment D : [dD];
fragment E : [eE];
fragment F : [fF];
fragment G : [gG];
fragment H : [hH];
fragment I : [iI];
fragment J : [jJ];
fragment K : [kK];
fragment L : [lL];
fragment M : [mM];
fragment N : [nN];
fragment O : [oO];
fragment P : [pP];
fragment Q : [qQ];
fragment R : [rR];
fragment S : [sS];
fragment T : [tT];
fragment U : [uU];
fragment V : [vV];
fragment W : [wW];
fragment X : [xX];
fragment Y : [yY];
fragment Z : [zZ];
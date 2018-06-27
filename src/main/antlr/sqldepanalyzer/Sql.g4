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
  | cte_clause? select_stmt
  | cte_clause? insert_stmt
  | grant_stmt
  | alter_table_stmt
  | analyze_stmt
  ;

create_database_stmt
  : CREATE (DATABASE | SCHEMA) (IF NOT EXISTS)? IDENTIFIER (AUTHORIZATION IDENTIFIER)?
  ;

create_table_stmt
  : CREATE TEMPORARY? EXTERNAL? TABLE (IF NOT EXISTS)? table_identifier
  (create_table_field_list | create_table_like_clause)?
  create_table_comment_clause?
  create_table_partition_clause?
  create_table_row_format_clause?
  create_table_stored_as_clause?
  create_table_location_clause?
  create_table_tblproperties_clause?
  anything* // Extra stuff
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

create_table_like_clause
  : '(' LIKE table_identifier ')'
  | LIKE table_identifier
  ;

create_table_properties_list
  : '(' (create_table_property (',' create_table_property)*)? ')'
  ;

create_table_property
  : STRING_LITERAL OP_EQ STRING_LITERAL
  ;

create_view_stmt
  : CREATE VIEW (IF NOT EXISTS)? table_identifier AS select_stmt
  ;

set_stmt
  : SET config_variable OP_EQ anything*
  ;

add_stmt
  : ADD anything*
  ;

cte_clause
  : WITH cte_sub_clause (',' cte_sub_clause)*
  ;

cte_sub_clause
  : indentifier AS '(' select_stmt ')'
  ;

analyze_stmt
  : ANALYZE table_identifier
  ;

grant_stmt
  : GRANT anything*
  ;

field_spec
  : indentifier type (NOT NULL)? (DEFAULT expression)? SORTKEY? DISTKEY? indentifier* create_table_comment_clause?// extra stuff..
  ;

alter_table_stmt
  : ALTER TABLE table_identifier anything*
  ;

type
  : IDENTIFIER ('(' NUMERIC_LITERAL ( ',' NUMERIC_LITERAL)?')')?
  | IDENTIFIER '<' type '>' // array
  | IDENTIFIER '<' indentifier ':' type (',' indentifier ':' type)* '>' // struct
  | IDENTIFIER '<' indentifier (',' indentifier)* '>' // map
  ;

select_stmt
  : select_clause
  from_clause?
  where_clause?
  group_by_clause?
  order_by_clause?
  limit_clause?
  ;

select_clause
  : SELECT DISTINCT? named_expression (',' named_expression)*
  ;

insert_stmt
  : INSERT OVERWRITE? TABLE table_identifier
  (PARTITION '(' partition_spec (',' partition_spec)* ')')?
  select_stmt
  ;

partition_spec
  : indentifier
  | indentifier OP_EQ literal
  ;

from_clause
  : FROM source IDENTIFIER?
  ;

where_clause
  : WHERE expression
  ;

group_by_clause
  : GROUP BY expression (',' expression)*
  ;

order_by_clause
  : ORDER BY order_expression (',' order_expression)*
  ;

limit_clause
  : LIMIT NUMERIC_LITERAL
  ;

order_expression
  : expression
  | expression (ASC | DESC)
  ;

source
  : table_identifier
  | '(' select_stmt ')'
  ;

named_expression
  : expression
  | expression AS indentifier
  ;

expression
  : '(' expression ')'
  | function_call
  | qualified_identifier
  | literal
  | expression OP_AND expression
  | expression OP_OR expression
  | expression ( OP_MULT | OP_DIV ) expression
  | expression ( OP_PLUS | OP_MINUS ) expression
  | expression ( OP_GT | OP_GTE | OP_LT | OP_LTE ) expression
  | expression ( OP_EQ | OP_NEQ ) expression
  | expression IS NOT? NULL
  | expression ( OP_AND | OP_OR ) expression
  | expression NOT? LIKE STRING_LITERAL
  | expression NOT? IN '(' (literal (',' literal)*)? ')'
  | '*'
  | indentifier OP_DOT '*'
  | CAST '(' expression AS type ')'
  ;

function_call
  : indentifier '(' DISTINCT? (expression (',' expression)*)? ')'
  | indentifier '(' (expression (',' expression)*)? ')' OVER '('(PARTITION BY (expression (',' expression)*)?)? order_by_clause?')'
  ;

table_identifier
  : qualified_identifier
  ;

qualified_identifier
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
  | ALTER
  | ANALYZE
  | AS
  | ASC
  | AUTHORIZATION
  | BY
  | CAST
  | COMMENT
  | CREATE
  | DATABASE
  | DEFAULT
  | DELIMITED
  | DESC
  | DISTKEY
  | DISTINCT
  | ESCAPED
  | EXISTS
  | EXTERNAL
  | FALSE
  | FIELDS
  | FORMAT
  | FROM
  | GRANT
  | GROUP
  | IF
  | IN
  | INSERT
  | IS
  | INPUTFORMAT
  | LIKE
  | LIMIT
  | LOCATION
  | NOT
  | NULL
  | ORDER
  | OUTPUTFORMAT
  | OVER
  | OVERWRITE
  | PARTITION
  | PARTITIONED
  | ROW
  | SCHEMA
  | SERDE
  | SERDEPROPERTIES
  | SELECT
  | SET
  | SORTKEY
  | STORED
  | TABLE
  | TBLPROPERTIES
  | TEMPORARY
  | TERMINATED
  | TRUE
  | WITH
  | WHERE
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

literal
  : STRING_LITERAL
  | NUMERIC_LITERAL
  | TRUE
  | FALSE
  | NULL
  ;


// Key words
ADD: A D D;
ALTER: A L T E R;
ANALYZE: A N A L Y Z E;
AS: A S;
ASC: A S C;
AUTHORIZATION: A U T H O R I Z A T I O N;
BY: B Y;
CAST: C A S T;
COMMENT: C O M M E N T;
CREATE: C R E A T E;
DATABASE: D A T A B A S E;
DEFAULT: D E F A U L T;
DELIMITED: D E L I M I T E D;
DESC: D E S C;
DISTKEY: D I S T K E Y;
DISTINCT: D I S T I N C T;
ESCAPED: E S C A P E D;
EXISTS: E X I S T S;
EXTERNAL: E X T E R N A L;
FALSE: F A L S E;
FIELDS: F I E L D S;
FORMAT: F O R M A T;
FROM: F R O M;
GRANT: G R A N T;
GROUP: G R O U P;
IF: I F;
IN: I N;
INSERT: I N S E R T;
IS: I S;
INPUTFORMAT: I N P U T F O R M A T;
LIKE: L I K E;
LIMIT: L I M I T;
LOCATION: L O C A T I O N;
NOT: N O T;
NULL: N U L L;
ORDER: O R D E R;
OUTPUTFORMAT: O U T P U T F O R M A T;
OVER: O V E R;
OVERWRITE: O V E R W R I T E;
PARTITION: P A R T I T I O N;
PARTITIONED: P A R T I T I O N E D;
ROW: R O W;
SCHEMA: S C H E M A;
SERDE: S E R D E;
SERDEPROPERTIES: S E R D E P R O P E R T I E S;
SELECT: S E L E C T;
SET: S E T;
SORTKEY: S O R T K E Y;
STORED: S T O R E D;
TABLE: T A B L E;
TBLPROPERTIES: T B L P R O P E R T I E S;
TEMPORARY: T E M P O R A R Y;
TERMINATED: T E R M I N A T E D;
TRUE: T R U E;
WITH: W I T H;
WHERE: W H E R E;
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
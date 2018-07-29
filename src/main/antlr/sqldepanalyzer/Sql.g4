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
  | top_level_select_stmt
  | top_level_insert_stmt
  | grant_stmt
  | alter_table_stmt
  | analyze_stmt
  | create_function_stmt
  | create_macro_stmt
  | drop_table_stmt
  | drop_view_stmt
  | truncate_table_stmt
  | BEGIN ';' (stmt ';')* END
  ;

top_level_select_stmt
  : cte_clause? select_stmt
  ;

top_level_insert_stmt
  : cte_clause? insert_stmt
  | cte_clause? hive_multi_insert_stmt
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
  create_table_lines_format_clause?
  create_table_stored_as_clause?
  create_table_location_clause?
  create_table_tblproperties_clause?
  | CREATE TEMPORARY TABLE table_identifier AS select_stmt
  | CREATE TEMPORARY TABLE table_identifier AS '(' select_stmt ')'
  ;

create_table_comment_clause
  : COMMENT STRING_LITERAL
  ;

create_table_partition_clause
  : PARTITIONED BY create_table_field_list
  ;

create_table_lines_format_clause
  : LINES TERMINATED BY STRING_LITERAL
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
  : '(' (field_spec (',' field_spec)*)? (',' PRIMARY KEY '(' identifier (',' identifier)* ')')?')'
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

create_function_stmt
  : CREATE TEMPORARY? FUNCTION anything*
  ;

create_macro_stmt
  : CREATE TEMPORARY? MACRO anything*
  ;

cte_clause
  : WITH cte_sub_clause (',' cte_sub_clause)*
  ;

cte_sub_clause
  : identifier AS '(' select_stmt ')'
  ;

analyze_stmt
  : ANALYZE table_identifier
  ;

drop_table_stmt
  : DROP TABLE (IF EXISTS)? table_identifier CASCADE?
  ;

drop_view_stmt
  : DROP VIEW (IF EXISTS)? table_identifier CASCADE?
  ;

truncate_table_stmt
  : TRUNCATE TABLE table_identifier
  ;

grant_stmt
  : GRANT anything*
  ;

field_spec
  : identifier type (NOT NULL)? (DEFAULT expression)? SORTKEY? DISTKEY? identifier* create_table_comment_clause?// extra stuff..
  ;

alter_table_stmt
  : ALTER TABLE table_identifier anything*
  ;

type
  : identifier ('(' NUMERIC_LITERAL ( ',' NUMERIC_LITERAL)?')')?
  | IDENTIFIER OP_LT type OP_GT // array
  | IDENTIFIER OP_LT identifier ':' type (',' identifier ':' type)* OP_GT // struct
  | IDENTIFIER OP_LT identifier (',' identifier)* OP_GT // map
  ;

select_stmt
  : select_clause
  from_clause?
  where_clause?
  group_by_clause?
  having_clause?
  order_by_clause?
  limit_clause?
  cluster_by_clause?
  | select_stmt UNION ALL? select_stmt
  ;

select_clause
  : SELECT DISTINCT? named_expression (',' named_expression)*
  ;

insert_stmt
  : insert_clause
  (select_stmt | '(' select_stmt ')')
  ;

insert_clause
  : INSERT (OVERWRITE | INTO) TABLE? table_identifier
      (PARTITION '(' partition_spec (',' partition_spec)* ')')?
  ;

hive_multi_insert_stmt
  : from_clause (insert_stmt)*
  ;

partition_spec
  : identifier
  | identifier OP_EQ literal
  ;

from_clause
  : FROM sources
  ;

sources
  : source (',' source)* // people shouldnt do this!!
  | source ((LEFT | RIGHT)? (INNER | (FULL? OUTER))? SEMI? JOIN source (ON expression)?)*
  ;

where_clause
  : WHERE expression
  ;

group_by_clause
  : GROUP BY expression_list (GROUPING SETS '(' (grouping_set (',' grouping_set)* )? ')')?
  ;

grouping_set
  : '(' expression_list ')'
  ;

having_clause
  : HAVING expression
  ;

order_by_clause
  : ORDER BY order_expression (',' order_expression)*
  ;

limit_clause
  : LIMIT NUMERIC_LITERAL
  ;

cluster_by_clause
  : CLUSTER BY expression_list
  | DISTRIBUTE BY expression_list (SORT BY expression_list)?
  ;

order_expression
  : expression (ASC | DESC)? (NULLS (FIRST | LAST))?
  ;

source
  : table_identifier (AS? identifier)?
  | '(' select_stmt ')' (AS? identifier)?
  | source LATERAL VIEW OUTER? expression identifier (AS identifier)?
  ;

named_expression
  : expression
  | expression AS? identifier
  ;

expression
  : '(' expression ')'
  | function_call
  | qualified_identifier
  | literal
  | expression OP_CONCAT expression
  | expression OP_AND expression
  | expression OP_OR expression
  | expression ( OP_MULT | OP_DIV ) expression
  | expression ( OP_PLUS | OP_MINUS ) expression
  | expression ( OP_GT | OP_GTE | OP_LT | OP_LTE ) expression
  | expression ( OP_EQ | OP_NEQ | OP_NS_EQ ) expression
  | expression IS NOT? NULL
  | expression ( OP_AND | OP_OR ) expression
  | expression NOT? LIKE STRING_LITERAL
  | expression NOT? IN '(' (literal (',' literal)*)? ')'
  | expression NOT? IN '(' select_stmt ')'
  | expression NOT? IN hive_var_literal
  | NOT expression
  | '*'
  | expression OP_DOT identifier
  | identifier OP_DOT '*'
  | CAST '(' expression AS type ')'
  | expression '::' type // pg style cast
  | expression BETWEEN expression OP_AND expression
  | expression BETWEEN hive_var_literal
  | CASE expression? (WHEN expression THEN expression)* (ELSE expression)? END
  | expression '[' expression ']'
  | OP_MINUS expression
  ;

function_call
  : identifier '(' DISTINCT? (expression (',' expression)*)? ')'
  | identifier '(' identifier FROM expression ')' // Werid syntax for hives extract function
  | identifier '(' expression_list ')' OVER '('(PARTITION BY expression_list)? order_by_clause? window_row_spec? ')'
  ;

window_row_spec
  : ROWS BETWEEN (UNBOUNDED | NUMERIC_LITERAL) PRECEDING OP_AND (UNBOUNDED | NUMERIC_LITERAL) PRECEDING
  ;

expression_list
  :  (expression (',' expression)*)?
  ;

table_identifier
  : qualified_identifier
  ;

qualified_identifier
  : identifier (OP_DOT identifier)?
  ;

config_variable
  : (identifier ':')? identifier ((OP_DOT | OP_MINUS) identifier)*
  ;

identifier
  : IDENTIFIER
  | keyword
  ;

// Rules needed to make the set statement work

anything
  : keyword
  | operator
  | IDENTIFIER
  | literal
  | ':'
  | '{'
  | '}'
  | '('
  | ')'
  | ','
  ;

keyword
  : ADD
  | ALL
  | ALTER
  | ANALYZE
  | AS
  | ASC
  | AUTHORIZATION
  | BEGIN
  | BETWEEN
  | BY
  | CASCADE
  | CAST
  | CASE
  | CLUSTER
  | COMMENT
  | CREATE
  | DATABASE
  | DATE
  | DEFAULT
  | DELIMITED
  | DESC
  | DISTKEY
  | DISTINCT
  | DISTRIBUTE
  | DROP
  | ELSE
  | END
  | ESCAPED
  | EXISTS
  | EXTERNAL
  | FALSE
  | FIELDS
  | FIRST
  | FORMAT
  | FULL
  | FUNCTION
  | FROM
  | GRANT
  | GROUP
  | GROUPING
  | HAVING
  | IF
  | IN
  | INNER
  | INSERT
  | INTERVAL
  | INTO
  | IS
  | INPUTFORMAT
  | JOIN
  | KEY
  | LAST
  | LATERAL
  | LEFT
  | LIKE
  | LIMIT
  | LINES
  | LOCATION
  | MACRO
  | NOT
  | NULL
  | NULLS
  | ON
  | ORDER
  | OUTER
  | OUTPUTFORMAT
  | OVER
  | OVERWRITE
  | PARTITION
  | PARTITIONED
  | PRECEDING
  | PRIMARY
  | RIGHT
  | ROW
  | ROWS
  | SCHEMA
  | SERDE
  | SERDEPROPERTIES
  | SELECT
  | SEMI
  | SET
  | SETS
  | SORT
  | SORTKEY
  | STORED
  | TABLE
  | TBLPROPERTIES
  | TEMPORARY
  | TERMINATED
  | THEN
  | TRUE
  | TRUNCATE
  | UNBOUNDED
  | UNION
  | WITH
  | WHEN
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
  | OP_AND
  | OP_OR
  | OP_NS_EQ
  | OP_CONCAT
  ;

literal
  : DATE STRING_LITERAL
  | INTERVAL STRING_LITERAL IDENTIFIER?
  | STRING_LITERAL
  | NUMERIC_LITERAL
  | TRUE
  | FALSE
  | NULL
  | hive_var_literal
  ;

hive_var_literal
  : '${' config_variable '}'
  ;


// Key words
ADD: A D D;
ALL: A L L;
ALTER: A L T E R;
ANALYZE: A N A L Y Z E;
AS: A S;
ASC: A S C;
AUTHORIZATION: A U T H O R I Z A T I O N;
BEGIN: B E G I N;
BETWEEN: B E T W E E N;
BY: B Y;
CASCADE: C A S C A D E;
CASE: C A S E;
CAST: C A S T;
CLUSTER: C L U S T E R;
COMMENT: C O M M E N T;
CREATE: C R E A T E;
DATABASE: D A T A B A S E;
DATE: D A T E;
DEFAULT: D E F A U L T;
DELIMITED: D E L I M I T E D;
DESC: D E S C;
DISTKEY: D I S T K E Y;
DISTINCT: D I S T I N C T;
DISTRIBUTE: D I S T R I B U T E;
DROP: D R O P;
ELSE: E L S E;
END: E N D;
ESCAPED: E S C A P E D;
EXISTS: E X I S T S;
EXTERNAL: E X T E R N A L;
FALSE: F A L S E;
FIELDS: F I E L D S;
FIRST: F I R S T;
FORMAT: F O R M A T;
FROM: F R O M;
FULL: F U L L;
FUNCTION: F U N C T I O N;
GRANT: G R A N T;
GROUP: G R O U P;
GROUPING: G R O U P I N G;
HAVING: H A V I N G;
IF: I F;
IN: I N;
INNER: I N N E R;
INSERT: I N S E R T;
INTERVAL: I N T E R V A L;
INTO: I N T O;
IS: I S;
INPUTFORMAT: I N P U T F O R M A T;
JOIN: J O I N;
KEY: K E Y;
LAST: L A S T;
LATERAL: L A T E R A L;
LEFT: L E F T;
LIKE: L I K E;
LIMIT: L I M I T;
LINES: L I N E S;
LOCATION: L O C A T I O N;
MACRO: M A C R O;
NOT: N O T;
NULL: N U L L;
NULLS: N U L L S;
ON: O N;
ORDER: O R D E R;
OUTER: O U T E R;
OUTPUTFORMAT: O U T P U T F O R M A T;
OVER: O V E R;
OVERWRITE: O V E R W R I T E;
PARTITION: P A R T I T I O N;
PARTITIONED: P A R T I T I O N E D;
PRECEDING: P R E C E D I N G;
PRIMARY: P R I M A R Y;
RIGHT: R I G H T;
ROW: R O W;
ROWS: R O W S;
SCHEMA: S C H E M A;
SERDE: S E R D E;
SERDEPROPERTIES: S E R D E P R O P E R T I E S;
SELECT: S E L E C T;
SEMI: S E M I;
SET: S E T;
SETS: S E T S;
SORT: S O R T;
SORTKEY: S O R T K E Y;
STORED: S T O R E D;
TABLE: T A B L E;
TBLPROPERTIES: T B L P R O P E R T I E S;
TEMPORARY: T E M P O R A R Y;
TERMINATED: T E R M I N A T E D;
THEN: T H E N;
TRUE: T R U E;
TRUNCATE: T R U N C A T E;
UNBOUNDED: U N B O U N D E D;
UNION: U N I O N;
WITH: W I T H;
WHEN: W H E N;
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
OP_NS_EQ: '<=>';
OP_NEQ: '!=' | '<>';
OP_DOT: '.';
OP_CONCAT: '||';
OP_AND: A N D;
OP_OR: O R;

IDENTIFIER
 : [a-zA-Z_] [a-zA-Z_0-9]*
 | '`' ( ~'`' )* '`'
 ;

SINGLE_LINE_COMMENT
 : '--' ~[\r\n]* -> channel(HIDDEN)
 ;

MULTI_LINE_COMMENT
 : '/*' .*? '*/' -> channel(HIDDEN)
 ;

// Literals
NUMERIC_LITERAL
 : DIGIT+ ( '.' DIGIT* )?
 ;

STRING_LITERAL
 : '\'' (('\\' .) | ~('\\' | '\''))* '\''
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


[ x A B C D E]
[ A 1 0 1 1 1]
[ B 0 1 0 0 1]
[ C 1 0 1 1 1]
[ D 1 0 1 1 1]
[ E 1 1 1 1 1]


[x   A-C A-D A-E | B-E | C-A C-D C-E | D-A D-C D-E | E-A E-B E-C E-D]
RAW:
[x   C-A D-A E-A | E-B | A-C D-C E-C | A-D C-D E-D | A-E B-E C-E D-E]

Collapse dups:
[x   C-A   D-A   E-A |   E-B   |   E-C   |   C-D   E-D ]
[C-A   1   0.3   0.1     0.2       0.3       0.2   0.2 ]
[D-A   0.2  1    0.2 ...]
[E-A   0.3  0.2   1 ....]
-----------------

===============================================================

keynode A: neighbours [B* C D E]

row1 A:

S(B*a, Ca), S(B*a,Da), S(B*a, Ea)
S(Ca,Da), S(Ca,Ea)
S(Da,Ea)
=
S(B,C), S(B,D), S(B,E)
S(C,D), S(C,E)
S(D,E)

keynode B: neighbours [A*, C*, D*, E]
row2 B:
S(A*b, C*b), S(A*b,D*b), S(A*b,Eb)
S(C*,D*), S(C*,E)
S(D*,E)
=
S(A,C),S(A,D),S(A,E)
S(C,D),S(C,E)
S(D,E)

[x B-A C-A D-A E-A ]
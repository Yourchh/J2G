Expresion o condicion:
b * c + d > a * d
Variables:
a (id1): 10
b (id3): 5
c (id5): 20
d (id7): 10
pila segment para stack 'stack'
        db 1024 dup('stack')
pila ends

datos segment para public 'data'
	; Variables de la expresion
	id1 dw 10 	; a
	id3 dw 5 	; b
	id5 dw 20 	; c
	id7 dw 10 	; d

	; Temporales (segun traza de tablas_analisis.txt)
	t1 dw ?
	t2 dw ?
	t3 dw ?
	t4 dw ?

datos ends
codigo segment para public 'code'
        public compa
compa proc far
        assume cs:codigo, ds:datos, ss:pila
        push ds
        mov ax,0
        push ax

        mov ax,datos
        mov ds,ax

;--- Inicio del codigo de la expresion ---

	; t1 = id3 * id5
	mov dx, 0
	mov ax, id3
	mov bx, id5
	mul bx
	mov t1, ax

	; t2 = t1 + id7
	mov ax, t1
	add ax, id7
	mov t2, ax

	; t3 = id1 * id7
	mov dx, 0
	mov ax, id1
	mov bx, id7
	mul bx
	mov t3, ax

	; t4 = t2 > t3
	mov ax, t2
	mov bx, t3
	cmp ax, bx
	jg t4_true
	mov t4, 0
	jmp t4_end
t4_true:
	mov t4, 1
t4_end:

;--- Fin del codigo de la expresion ---
; El resultado booleano final esta en t4

        mov ah,7
        int 21
        ret

compa endp
        codigo ends
        end compa

----------------------------------------------------
Expresion o condicion:
((a + 5) * 100) > d && a <= 10 || b != c
Variables:
a (id1): 10
b (id3): 5
c (id5): 20
d (id7): 10
pila segment para stack 'stack'
        db 1024 dup('stack')
pila ends

datos segment para public 'data'
	; Variables de la expresion
	id18 dw 100 	; 100
	id2 dw 10 	; 10
	id1 dw 10 	; a
	id4 dw 5 	; 5
	id3 dw 5 	; b
	id5 dw 20 	; c
	id7 dw 10 	; d

	; Temporales (segun traza de tablas_analisis.txt)
	t1 dw ?
	t2 dw ?
	t3 dw ?
	t4 dw ?
	t5 dw ?
	t6 dw ?
	t7 dw ?

datos ends
codigo segment para public 'code'
        public compa
compa proc far
        assume cs:codigo, ds:datos, ss:pila
        push ds
        mov ax,0
        push ax

        mov ax,datos
        mov ds,ax

;--- Inicio del codigo de la expresion ---

	; t1 = id1 + id4
	mov ax, id1
	add ax, id4
	mov t1, ax

	; t1 = _ ? _
	; t2 = t1 * id18
	mov dx, 0
	mov ax, t1
	mov bx, id18
	mul bx
	mov t2, ax

	; t2 = _ ? _
	; t3 = t2 > id7
	mov ax, t2
	mov bx, id7
	cmp ax, bx
	jg t3_true
	mov t3, 0
	jmp t3_end
t3_true:
	mov t3, 1
t3_end:

	; t4 = id1 <= id2
	mov ax, id1
	mov bx, id2
	cmp ax, bx
	jle t4_true
	mov t4, 0
	jmp t4_end
t4_true:
	mov t4, 1
t4_end:

	; t5 = t3 && t4
	mov ax, t3
	mov bx, t4
	and ax, bx
	mov t5, ax

	; t6 = id3 != id5
	mov ax, id3
	mov bx, id5
	cmp ax, bx
	jne t6_true
	mov t6, 0
	jmp t6_end
t6_true:
	mov t6, 1
t6_end:

	; t7 = t5 || t6
	mov ax, t5
	mov bx, t6
	or ax, bx
	mov t7, ax

;--- Fin del codigo de la expresion ---
; El resultado booleano final esta en t7

        mov ah,7
        int 21
        ret

compa endp
        codigo ends
        end compa

----------------------------------------------------
Expresion o condicion:
! ((a - b / 2) == 0) || (a + b) == d && c >= 10
Variables:
a (id1): 10
b (id3): 5
c (id5): 20
d (id7): 10
pila segment para stack 'stack'
        db 1024 dup('stack')
pila ends

datos segment para public 'data'
	; Variables de la expresion
	id2 dw 10 	; 10
	id1 dw 10 	; a
	id20 dw 2 	; 2
	id3 dw 5 	; b
	id21 dw 0 	; 0
	id5 dw 20 	; c
	id7 dw 10 	; d

	; Temporales (segun traza de tablas_analisis.txt)
	t1 dw ?
	t2 dw ?
	t3 dw ?
	t4 dw ?
	t5 dw ?
	t6 dw ?
	t7 dw ?
	t8 dw ?
	t9 dw ?

datos ends
codigo segment para public 'code'
        public compa
compa proc far
        assume cs:codigo, ds:datos, ss:pila
        push ds
        mov ax,0
        push ax

        mov ax,datos
        mov ds,ax

;--- Inicio del codigo de la expresion ---

	; t1 = id3 / id20
	mov dx, 0
	mov ax, id3
	mov bx, id20
	div bx
	mov t1, ax

	; t2 = id1 - t1
	mov ax, id1
	sub ax, t1
	mov t2, ax

	; t2 = _ ? _
	; t3 = t2 == id21
	mov ax, t2
	mov bx, id21
	cmp ax, bx
	je t3_true
	mov t3, 0
	jmp t3_end
t3_true:
	mov t3, 1
t3_end:

	; t3 = _ ? _
	; t4 = ! t3
	mov ax, t3
	cmp ax, 0
	jne t4_false
	mov t4, 1
	jmp t4_end
t4_false:
	mov t4, 0
t4_end:

	; t5 = id1 + id3
	mov ax, id1
	add ax, id3
	mov t5, ax

	; t5 = _ ? _
	; t6 = t5 == id7
	mov ax, t5
	mov bx, id7
	cmp ax, bx
	je t6_true
	mov t6, 0
	jmp t6_end
t6_true:
	mov t6, 1
t6_end:

	; t7 = id5 >= id2
	mov ax, id5
	mov bx, id2
	cmp ax, bx
	jge t7_true
	mov t7, 0
	jmp t7_end
t7_true:
	mov t7, 1
t7_end:

	; t8 = t6 && t7
	mov ax, t6
	mov bx, t7
	and ax, bx
	mov t8, ax

	; t9 = t4 || t8
	mov ax, t4
	mov bx, t8
	or ax, bx
	mov t9, ax

;--- Fin del codigo de la expresion ---
; El resultado booleano final esta en t9

        mov ah,7
        int 21
        ret

compa endp
        codigo ends
        end compa

----------------------------------------------------
Expresion o condicion:
((a * 2) + c) > 30 || ! (a - b <= c) && d == 10
Variables:
a (id1): 10
b (id3): 5
c (id5): 20
d (id7): 10
pila segment para stack 'stack'
        db 1024 dup('stack')
pila ends

datos segment para public 'data'
	; Variables de la expresion
	id2 dw 10 	; 10
	id1 dw 10 	; a
	id20 dw 2 	; 2
	id3 dw 5 	; b
	id5 dw 20 	; c
	id23 dw 30 	; 30
	id7 dw 10 	; d

	; Temporales (segun traza de tablas_analisis.txt)
	t1 dw ?
	t2 dw ?
	t3 dw ?
	t4 dw ?
	t5 dw ?
	t6 dw ?
	t7 dw ?
	t8 dw ?
	t9 dw ?

datos ends
codigo segment para public 'code'
        public compa
compa proc far
        assume cs:codigo, ds:datos, ss:pila
        push ds
        mov ax,0
        push ax

        mov ax,datos
        mov ds,ax

;--- Inicio del codigo de la expresion ---

	; t1 = id1 * id20
	mov dx, 0
	mov ax, id1
	mov bx, id20
	mul bx
	mov t1, ax

	; t1 = _ ? _
	; t2 = t1 + id5
	mov ax, t1
	add ax, id5
	mov t2, ax

	; t2 = _ ? _
	; t3 = t2 > id23
	mov ax, t2
	mov bx, id23
	cmp ax, bx
	jg t3_true
	mov t3, 0
	jmp t3_end
t3_true:
	mov t3, 1
t3_end:

	; t4 = id1 - id3
	mov ax, id1
	sub ax, id3
	mov t4, ax

	; t5 = t4 <= id5
	mov ax, t4
	mov bx, id5
	cmp ax, bx
	jle t5_true
	mov t5, 0
	jmp t5_end
t5_true:
	mov t5, 1
t5_end:

	; t5 = _ ? _
	; t6 = ! t5
	mov ax, t5
	cmp ax, 0
	jne t6_false
	mov t6, 1
	jmp t6_end
t6_false:
	mov t6, 0
t6_end:

	; t7 = id7 == id2
	mov ax, id7
	mov bx, id2
	cmp ax, bx
	je t7_true
	mov t7, 0
	jmp t7_end
t7_true:
	mov t7, 1
t7_end:

	; t8 = t6 && t7
	mov ax, t6
	mov bx, t7
	and ax, bx
	mov t8, ax

	; t9 = t3 || t8
	mov ax, t3
	mov bx, t8
	or ax, bx
	mov t9, ax

;--- Fin del codigo de la expresion ---
; El resultado booleano final esta en t9

        mov ah,7
        int 21
        ret

compa endp
        codigo ends
        end compa

----------------------------------------------------
Expresion o condicion:
((a + 15) / c - d) == a || 4 <= c && ! (d >= a)
Variables:
a (id1): 10
c (id5): 20
d (id7): 10
pila segment para stack 'stack'
        db 1024 dup('stack')
pila ends

datos segment para public 'data'
	; Variables de la expresion
	id1 dw 10 	; a
	id5 dw 20 	; c
	id7 dw 10 	; d
	id26 dw 4 	; 4
	id25 dw 15 	; 15

	; Temporales (segun traza de tablas_analisis.txt)
	t1 dw ?
	t2 dw ?
	t3 dw ?
	t4 dw ?
	t5 dw ?
	t6 dw ?
	t7 dw ?
	t8 dw ?
	t9 dw ?

datos ends
codigo segment para public 'code'
        public compa
compa proc far
        assume cs:codigo, ds:datos, ss:pila
        push ds
        mov ax,0
        push ax

        mov ax,datos
        mov ds,ax

;--- Inicio del codigo de la expresion ---

	; t1 = id1 + id25
	mov ax, id1
	add ax, id25
	mov t1, ax

	; t1 = _ ? _
	; t2 = t1 / id5
	mov dx, 0
	mov ax, t1
	mov bx, id5
	div bx
	mov t2, ax

	; t3 = t2 - id7
	mov ax, t2
	sub ax, id7
	mov t3, ax

	; t3 = _ ? _
	; t4 = t3 == id1
	mov ax, t3
	mov bx, id1
	cmp ax, bx
	je t4_true
	mov t4, 0
	jmp t4_end
t4_true:
	mov t4, 1
t4_end:

	; t5 = id26 <= id5
	mov ax, id26
	mov bx, id5
	cmp ax, bx
	jle t5_true
	mov t5, 0
	jmp t5_end
t5_true:
	mov t5, 1
t5_end:

	; t6 = id7 >= id1
	mov ax, id7
	mov bx, id1
	cmp ax, bx
	jge t6_true
	mov t6, 0
	jmp t6_end
t6_true:
	mov t6, 1
t6_end:

	; t6 = _ ? _
	; t7 = ! t6
	mov ax, t6
	cmp ax, 0
	jne t7_false
	mov t7, 1
	jmp t7_end
t7_false:
	mov t7, 0
t7_end:

	; t8 = t5 && t7
	mov ax, t5
	mov bx, t7
	and ax, bx
	mov t8, ax

	; t9 = t4 || t8
	mov ax, t4
	mov bx, t8
	or ax, bx
	mov t9, ax

;--- Fin del codigo de la expresion ---
; El resultado booleano final esta en t9

        mov ah,7
        int 21
        ret

compa endp
        codigo ends
        end compa

----------------------------------------------------

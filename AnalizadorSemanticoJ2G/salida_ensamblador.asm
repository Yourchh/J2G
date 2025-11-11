pila segment para stack 'stack'
        db 1024 dup('stack')
pila ends

datos segment para public 'data'
	; --- Mensajes y Literales ---
	saltoLinea_msg db 0Dh, 0Ah, '$'
	msg_true db "TRUE$"
	msg_false db "FALSE$"
	msg_prompt_bool db "Ingrese 1 para TRUE, 0 para FALSE: $"
	msg4 db "Se cumplió la condición 3$"
	msg3 db "Se cumplió la condición 2$"
	msg1 db "hola mundo$"
	msg5 db "Se cumplió la condición 4$"
	msg2 db "Se cumplió la condición 1$"
	msg6 db "Esta condición tiene un error semántico$"

	; --- Variables y Literales del Programa ---
	id1 dw 10 	; a
	id3 dw 5 	; b
	id5 dw 20 	; c
	id7 dw 10 	; d
	id18 dw 100 	; 100
	id2 dw 10 	; 10
	id4 dw 5 	; 5
	id20 dw 2 	; 2
	id21 dw 0 	; 0
	id12 dw 23 	; var1
	id24 dw 30 	; 30
	id27 dw 4 	; 4
	id26 dw 15 	; 15

	; --- Temporales ---
	t4 dw ?
	t1 dw ?
	t2 dw ?
	t3 dw ?
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

;--- Inicio del codigo principal ---

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

	; INICIO IF_STMT: b * c + d > a * d
	mov ax, t4
	cmp ax, 0
	je if_end_1
	; PRINT: Print ("hola mundo");
	IMPRIMIR_MSG msg1
	SALTO_LINEA

if_end_1:

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

	; INICIO IF_STMT: ((a + 5) * 100) > d && a <= 10 || b != c
	mov ax, t7
	cmp ax, 0
	je if_end_2
	; PRINT: Print ("Se cumplió la condición 1");
	IMPRIMIR_MSG msg2
	SALTO_LINEA

if_end_2:

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

	; INICIO IF_STMT: ! ((a - b / 2) == 0) || (a + b) == d && c >= 10
	mov ax, t9
	cmp ax, 0
	je if_end_3
	; PRINT: Print ("Se cumplió la condición 2");
	IMPRIMIR_MSG msg3
	SALTO_LINEA

if_end_3:

	; ASIGNACION: var1 := Input (). Int ();
	mov ax, id23
	mov id12, ax

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
	; t3 = t2 > id24
	mov ax, t2
	mov bx, id24
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

	; INICIO IF_STMT: ((a * 2) + c) > 30 || ! (a - b <= c) && d == 10
	mov ax, t9
	cmp ax, 0
	je if_end_4
	; PRINT: Print ("Se cumplió la condición 3");
	IMPRIMIR_MSG msg4
	SALTO_LINEA

if_end_4:

	; t1 = id1 + id26
	mov ax, id1
	add ax, id26
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

	; t5 = id27 <= id5
	mov ax, id27
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

	; INICIO IF_STMT: ((a + 15) / c - d) == a || 4 <= c && ! (d >= a)
	mov ax, t9
	cmp ax, 0
	je if_end_5
	; PRINT: Print ("Se cumplió la condición 4");
	IMPRIMIR_MSG msg5
	SALTO_LINEA

if_end_5:

	; INICIO IF_STMT: (a + b - mensaje) * (a / 5) <= 10 && a == b || esValido != FALSE
	mov ax, null
	cmp ax, 0
	je if_end_6
	; PRINT: Print ("Esta condición tiene un error semántico");
	IMPRIMIR_MSG msg6
	SALTO_LINEA

if_end_6:

	; INICIO IF_STMT: (a * b + 50 != 10 && (esValido) > b || a <= 20)
	mov ax, null
	cmp ax, 0
	je if_end_7
	; PRINT: Print ("Esta condición tiene un error semántico");
	IMPRIMIR_MSG msg6
	SALTO_LINEA

if_end_7:

	; INICIO IF_STMT: (a * 2 + esValido) != 30 && (b - 1) > 0 || a <= 10
	mov ax, null
	cmp ax, 0
	je if_end_8
	; PRINT: Print ("Esta condición tiene un error semántico");
	IMPRIMIR_MSG msg6
	SALTO_LINEA

if_end_8:


;--- Fin del codigo principal ---



; --- INICIO MACROS ---
IMPRIMIR_CADENA_DX MACRO
    LOCAL imprimir_proc_call_site_macro
    push ax
    push dx
    call PROC_ImprimirCadenaDX
    pop dx
    pop ax
ENDM

IMPRIMIR_MSG MACRO msg_label
    LOCAL print_msg_site_macro
    push dx
    lea dx, msg_label
    IMPRIMIR_CADENA_DX
    pop dx
ENDM

SALTO_LINEA MACRO
    IMPRIMIR_MSG saltoLinea_msg
ENDM

; --- FIN MACROS ---


; --- INICIO PROCEDIMIENTOS ---
PROC_ImprimirCadenaDX PROC FAR
    push ax
	mov ah, 09h
	int 21h
    pop ax
	retf
PROC_ImprimirCadenaDX ENDP

PROC_MostrarNumeroDecimal PROC FAR
	push bp
	mov bp, sp
	push ax
	push bx
	push cx
	push dx

	mov ax, [bp+6]
	mov bx, 10
	xor cx, cx

	cmp ax, 0
	jne convertir_digitos_num_local_pmnd_procs
	mov dl, '0'
	mov ah, 02h
	int 21h
	jmp fin_imprimir_num_local_pmnd_procs

convertir_digitos_num_local_pmnd_procs:
convertir_loop_num_local_pmnd_procs:
	xor dx, dx
	div bx
	push dx
	inc cx
	cmp ax, 0
	jne convertir_loop_num_local_pmnd_procs

imprimir_loop_num_local_pmnd_procs:
	pop dx
	add dl, '0'
	mov ah, 02h
	int 21h
	loop imprimir_loop_num_local_pmnd_procs

fin_imprimir_num_local_pmnd_procs:
	pop dx
	pop cx
	pop bx
	pop ax
	pop bp
	retf 2
PROC_MostrarNumeroDecimal ENDP

PROC_CapturarNumeroDecimal PROC FAR
    push bp
    mov bp, sp
    push bx
    push cx
    push dx
    push si

    xor bx, bx

input_digit_loop_cap_local_pcnd_procs:
    mov ah, 01h
    int 21h

    cmp al, 0Dh
    je input_done_cap_local_pcnd_procs

    cmp al, '0'
    jl input_digit_loop_cap_local_pcnd_procs
    cmp al, '9'
    jg input_digit_loop_cap_local_pcnd_procs

    sub al, '0'
    mov ah, 0

    push ax
    mov ax, bx
    mov si, 10
    mul si
    mov bx, ax
    pop ax
    add bx, ax

    jmp input_digit_loop_cap_local_pcnd_procs

input_done_cap_local_pcnd_procs:
    mov ax, bx
    clc

    pop si
    pop dx
    pop cx
    pop bx
    pop bp
    retf
PROC_CapturarNumeroDecimal ENDP

; --- FIN PROCEDIMIENTOS ---

        mov ah,7
        int 21
        ret

compa endp
        codigo ends
        end compa

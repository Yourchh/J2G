pila segment para stack 'stack'
        db 1024 dup('stack')
pila ends

datos segment para public 'data'
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

;codigo
        mov ah,7
        int 21
        ret

compa endp
        codigo ends
        end compa

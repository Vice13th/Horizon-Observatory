# Usage: python3 tools/kotlin_balance_check.py <file.kt>...   (string-template aware brace/paren balance; not a compiler)
import sys
def balance(s):
    i=0;n=len(s);b=p=k=0;mode=['code'];tmpl=[]
    while i<n:
        c=s[i];m=mode[-1]
        if m=='code':
            if s.startswith('//',i):
                j=s.find('\n',i); i=n if j<0 else j; continue
            if s.startswith('/*',i):
                j=s.find('*/',i+2); i=n if j<0 else j+2; continue
            if s.startswith('"""',i): mode.append('raw'); i+=3; continue
            if c=='"': mode.append('str'); i+=1; continue
            if c=="'":
                j=i+1
                if j<n and s[j]=='\\': j+=2
                else: j+=1
                while j<n and s[j]!="'": j+=1
                i=j+1; continue
            if c=='{': b+=1
            elif c=='}':
                if tmpl and b==tmpl[-1]: tmpl.pop(); mode.pop()
                else: b-=1
            elif c=='(' : p+=1
            elif c==')': p-=1
            elif c=='[': k+=1
            elif c==']': k-=1
            i+=1; continue
        if m=='str' and c=='\\': i+=2; continue
        if m=='str' and c=='"': mode.pop(); i+=1; continue
        if m=='raw' and s.startswith('"""',i): mode.pop(); i+=3; continue
        if c=='$' and i+1<n and s[i+1]=='{': tmpl.append(b); mode.append('code'); i+=2; continue
        i+=1
    return b,p,k,mode
for f in sys.argv[1:]:
    r=balance(open(f).read()); print('BAL' if r[:3]==(0,0,0) and r[3]==['code'] else 'UNBAL',r,f.split('/')[-1])

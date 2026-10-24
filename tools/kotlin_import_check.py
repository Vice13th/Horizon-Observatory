# Usage (from app/src): python3 ../../tools/kotlin_import_check.py <file.kt>...  — heuristic missing-import finder (not a compiler). Known false positives: fn:size, fn:height, fn:getValue when used as properties/stdlib members.
import re,sys,glob,os
def code_only(s):
    out=[];i=0;n=len(s);mode=['code'];tm=[];b=0
    while i<n:
        c=s[i];m=mode[-1]
        if m=='code':
            if s.startswith('//',i):
                j=s.find('\n',i); j=n if j<0 else j; out.append(' '*(j-i)); i=j; continue
            if s.startswith('/*',i):
                j=s.find('*/',i+2); j=n if j<0 else j+2; out.append(' '*(j-i)); i=j; continue
            if s.startswith('"""',i): mode.append('raw'); out.append('   '); i+=3; continue
            if c=='"': mode.append('str'); out.append(' '); i+=1; continue
            if c=="'":
                j=i+1
                j+= 2 if (j<n and s[j]=='\\') else 1
                while j<n and s[j]!="'": j+=1
                out.append(' '*(j+1-i)); i=j+1; continue
            if c=='{': b+=1
            if c=='}':
                if tm and b==tm[-1]: tm.pop(); mode.pop(); out.append(' '); i+=1; continue
                b-=1
            out.append(c); i+=1; continue
        if m=='str' and c=='\\': out.append('  '); i+=2; continue
        if m=='str' and c=='"': mode.pop(); out.append(' '); i+=1; continue
        if m=='raw' and s.startswith('"""',i): mode.pop(); out.append('   '); i+=3; continue
        if c=='$' and i+1<n and s[i+1]=='{': tm.append(b); mode.append('code'); out.append('  '); i+=2; continue
        out.append(' ' if c!='\n' else '\n'); i+=1
    return ''.join(out)
DEFAULT=set('''String Int Long Double Float Boolean Char Byte Short Number Any Unit Nothing Array List MutableList Map MutableMap Set MutableSet Pair Triple Exception Throwable RuntimeException IllegalStateException IllegalArgumentException Regex StringBuilder ArrayList LinkedHashMap HashMap Charsets Math System Thread Comparable Iterable Sequence Result Function Volatile Suppress Deprecated JvmStatic JvmOverloads JvmField Lazy Collection Iterator Error ByteArray DoubleArray IntArray LongArray FloatArray BooleanArray CharArray Enum Annotation Object Void Override Test Ignore Before After UnsupportedOperationException NumberFormatException IndexOutOfBoundsException NoSuchElementException CharSequence Appendable StackOverflowError OutOfMemoryError Process Runtime Character Integer ClassCastException Locale'''.split())
LOWER='remember mutableStateOf mutableFloatStateOf getValue setValue pointerInput detectDragGestures awaitEachGesture awaitFirstDown drawBehind drawWithContent graphicsLayer onSizeChanged drawIntoCanvas translate lerp background fillMaxWidth fillMaxSize fillMaxHeight padding height size shadow clip delay runBlocking staticCompositionLocalOf animateFloatAsState spring rememberSaveable launch withContext rememberLauncherForActivityResult'.split()
root='main/kotlin'
pkgdecl={}
for f in glob.glob('**/*.kt',recursive=True):
    s=open(f).read(); m=re.search(r'^package\s+([\w.]+)',s,re.M)
    p=m.group(1) if m else ''
    names=set(re.findall(r'^\s*(?:(?:private|internal|public|data|enum|sealed|abstract|open|inline|const|suspend|operator|infix|tailrec|annotation|value|fun interface)\s+)*(?:class|object|interface|fun|val|var|typealias)\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(\w+)',s,re.M))
    pkgdecl.setdefault(p,set()).update(names)
for f in sys.argv[1:]:
    s=open(f).read(); code=code_only(s)
    m=re.search(r'^package\s+([\w.]+)',s,re.M); pkg=m.group(1) if m else ''
    imps=re.findall(r'^import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$',s,re.M)
    imported=set(); wild=set()
    for path,alias in imps:
        imported.add(alias or path.split('.')[-1])
    for l in re.findall(r'^import\s+([\w.]+)\.\*',s,re.M): wild.add(l)
    local=set(re.findall(r'\b(?:class|object|interface|typealias|fun|val|var)\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(\w+)',code))
    local|=set(re.findall(r'\b(\w+)\s*[:,)]\s*',''))  # placeholder
    enumentries=set(re.findall(r'^\s{4}([A-Z][A-Z_0-9]+)\(',code,re.M))
    unresolved=set()
    for m in re.finditer(r'(?<![\w.@"])([A-Z][A-Za-z0-9_]*)\b',code):
        n=m.group(1)
        if n in imported or n in DEFAULT or n in local or n in pkgdecl.get(pkg,set()) or n in enumentries: continue
        if re.fullmatch(r'[A-Z][A-Z_0-9]+',n): # constants maybe local/top-level
            if n in local or re.search(r'\bconst val '+n+r'\b|\bval '+n+r'\b',code): continue
        unresolved.add(n)
    for m in re.finditer(r'@([A-Z]\w*)',code):
        n=m.group(1)
        if n not in imported and n not in DEFAULT and n not in pkgdecl.get(pkg,set()): unresolved.add('@'+n)
    for n in LOWER:
        if re.search(r'\b'+n+r'\b',code) and n not in imported and n not in local and n not in pkgdecl.get(pkg,set()):
            # member-ish names that need no import
            if n in ('size','height','padding','launch','delay','spring','translate','lerp') and False: pass
            unresolved.add('fn:'+n)
    print(os.path.basename(f), sorted(unresolved) if unresolved else 'OK')


let maxNumResults = 250
let baseURL = window.location.href.split('?')[0]
let typeIndex = {}
let lastCurrent = null

async function unzipDocs() {
	const {entries} = await unzipit.unzip('docs.zip');
	Object.values(entries).map(async(entry) => {
	
		let data = window.data = await entry.json()
		tree.innerHTML = '' // clear static tree
		createTypeIndex(data, null)
		createTree(tree, data, 0, null)
		
		openPathByURL()
	});
}
unzipDocs()

function openPathByURL(){
	let params = new URLSearchParams(window.location.search)
	let search = params.get('search') || ''
	if(search.length) {
		runSearch(search)
	} else {
		let firstPage = params.get('page') || 'me/anno/Engine'
		openPath(firstPage.split('.').join('/'))
	}
}

window.addEventListener('popstate', () => { openPathByURL() });

function createTypeIndex(data, path) {
	for(key in data) {
		if(key.length == 1) continue
		let subPath = path ? path + '/' + key : key
		typeIndex[key] = typeIndex[key] === undefined ? subPath : null
		typeIndex[subPath.split('/').join('.')] = subPath
		createTypeIndex(data[key], subPath)
	}
}

function endsWith(hay, needle){
	return hay.length >= needle.length &&
		hay.substr(hay.length-needle.length, needle.length) == needle
}

function runSearch(term) {
	let result = []
	search(data, term, null, result)
	searchResultsSec.style.display = ''
	docs.style.display = 'none'
	searchResults.innerHTML = ''
	if(result.length){
		if(term.indexOf(' ') < 0) {
			// try to find class, prefer to have it first
			let end = '/' + term
			result.forEach(r => {
				r[2] += (
					endsWith(r[0].toLowerCase(), end) ? 20 :
					r[0].toLowerCase().indexOf(end)>=0 ? 15 :
					-1 / r[1].length
				);
			})
			result.sort((a,b) => b[2]-a[2])
		}
		result.forEach(r => {
			let child = document.createElement('p')
			let path = r[0]
			child.onclick = () => { openPath(path) }
			child.style.cursor = 'pointer'
			child.innerHTML = '<span class="name">' + path + '</span>' + ': ' + r[1].map(ri => {
				ri = ri
					.split('<').join('&lt;')
					.split('>').join('&gt;')
					.split('\n').join('\\n')
					.split('<b>').join('')
					.split('</b>').join('')
				let i = 0
				let ril = ri.toLowerCase()
				let res = ''
				while(true) {
				// todo apply this earlier?, so we can highlight partial matching?
					let j = ril.indexOf(term,i)
					if(j >= 0) {
						res += ri.substring(i,j)
						res += '<b>'+ri.substr(j,term.length)+'</b>'
						i = j + term.length
					} else {
						res += ri.substring(i)
						break
					}
				}
				return '<span class="comment">' + res + '</span>'
			}).join(', ')
			searchResults.appendChild(child)
		})
		if(result.length >= maxNumResults) {
			let child = document.createElement('p')
			child.innerText = 'Cancelled search, because of too numerous results...'
			searchResults.appendChild(child)
		}
	} else searchResults.innerHTML = 'No results were found'
	let term2 = new URLSearchParams(window.location.search).get('search')
	if(term2 != term) window.history.pushState('?'+term, null, window.location.href.split('?')[0] + "?search=" + term)
}

searchBar.onkeydown = (e) => {
	if(e.keyCode == 13) {
		let term = searchBar.value.trim().toLowerCase()
		if(term.length) runSearch(term)
	}
}

function openPath(path) {
	let obj = data
	let parts = path.split('/')
	parts.forEach(part => {
		obj = obj[part] || obj
		obj.q?.()
	})
	let path1 = parts.slice(0,Math.max(parts.length-1,0)).join('/')
	displayClass(obj, path1, path, parts[parts.length-1])
}

// todo immediate list of search results (onCharTyped())
function containsSearchTerm(text0, term) {
// for score, use the length of the longest run
	let text = text0.toLowerCase()
	if(text.indexOf(term) >= 0) return term.length * Math.log(term.length+1) * 3
	// only apply advanced search, if text is a path?
	if(text.indexOf(' ') >= 0) return 0
	let anyLower = false
	for(let i=0;i<text.length;i++) {
		if(text[i] == text0[i]) {
			anyLower = true
			break
		}
	}
	let allUpper = !anyLower
	for(let i=0,j=0,li=-1,lr=0,ls=0;i<text.length;i++) {
		if(text[i] == term[j]) {
			// for score, value upper case letters more?
			let dlr = allUpper || text[i] == text0[i] ? 1 : 2
			if(li+1 == i) {
				lr += dlr
			} else {
				ls += lr * Math.log(lr+1)
				lr = dlr
			}
			if(++j == term.length) {
				ls += lr * Math.log(lr+1)
				console.log(text0, ls)
				return ls
			}
			li = i
		}
	}
	return 0
}

function search(data, term, path, result) {
	if(Array.isArray(data)) {
		for(let i=0;i<data.length;i++) {
			if(result.length >= maxNumResults) return
			search(data[i], term, path, result)
		}
	} else if(data && typeof data === 'object') {
		for(key in data) {
			if(result.length >= maxNumResults) return
			let pathI = path ? key.length == 1 ? path : path + '/' + key : key
			if(key.length > 1) {
				let score = containsSearchTerm(key, term)
				if(score) {
					let v = [pathI, key, score]
					addResult(result, v)
				}
			}
			search(data[key], term, pathI, result)
		}
	} else if(typeof data === 'string' || data instanceof String) {
		let score = containsSearchTerm(data, term)
		if(score) {
			let v = [path, data, score]
			addResult(result, v)
		}
	}
}

function addResult(result, v) {
	for(let i=0;i<result.length;i++){
		if(result[i][0] == v[0]) {
			if(result[i][1].indexOf(v[1]) < 0) {
				result[i][1].push(v[1])
			}
			result[i][2] = Math.max(v[2], result[i][2])
			return;
		}
	}
	result.push([v[0],[v[1]],v[2]])
}

function isUpper(c){
	return c == c.toUpperCase()
}

function sortObject(obj) {
	return Object.keys(obj)
		.filter(x => x.length > 1)
		.sort((a,b) => {
			return isUpper(a[0]) == isUpper(b[0]) ?
				a.length < 2 || b.length < 2 || isUpper(a[1]) == isUpper(b[1]) ?
				a.localeCompare(b) :
				isUpper(a[1]) ? -1 : 1 : isUpper(a[0]) ? 1 : -1;
		});
}

function isKeyword(k){
	return k[0] != '*' && k[0] != '@'
}

// define links for standard library
let stdlibBase = 'https://kotlinlang.org/api/latest/jvm/stdlib/'
let stdlib = {
	'kotlin.collections/': 'Map,List,Set,HashSet,HashMap,ArrayList,LinkedList,MutableList,MutableSet,MutableMap,Iterable,Iterator,Collection',
	'kotlin/': 'Boolean,Byte,Short,Char,Int,Long,Float,Double,UByte,UShort,UInt,ULong,String,Unit,BooleanArray,ByteArray,ShortArray,CharArray,IntArray,LongArray,FloatArray,DoubleArray,Array,String,Unit,Any,Nothing,CharSequence,Throwable,Exception,RuntimeException,Pair,Triple,Comparator,Comparable,Lazy',
	'kotlin.reflect/': 'KClass',
	'kotlin.text/': 'StringBuilder,Charset',
	'kotlin.io/java.io.': 'InputStream,OutputStream',
	'kotlin.sequences/': 'Sequence',
	'kotlin.native/': 'BitSet',
}
for(key in stdlib){
	let baseURL = stdlibBase + key
	stdlib[key].split(',').forEach(name => {
		let name1 = name.split('').map(x => x == x.toLowerCase() ? x : '-'+x.toLowerCase()).join('') + '/'
		typeIndex[name] = baseURL + name1
	})
}

let oracle = 'https://docs.oracle.com/javase/8/docs/api/java/'
let nioBase = oracle + 'nio/'
'ByteBuffer,ShortBuffer,IntBuffer,FloatBuffer'.split(',').forEach(key => {
	typeIndex[key] = nioBase + key + '.html'
})
typeIndex['LinkedBlockingQueue'] = oracle + 'util/concurrent/LinkedBlockingQueue.html'
typeIndex['WeakHashMap'] = oracle + 'util/WeakHashMap.html'
typeIndex['Queue'] = oracle + 'util/Queue.html'
typeIndex['URI'] = oracle + 'net/URI.html'
typeIndex['URL'] = oracle + 'net/URL.html'
typeIndex['Class'] = oracle + 'lang/Class.html'
typeIndex['Thread'] = oracle + 'lang/Thread.html'
typeIndex['Enum'] = oracle + 'lang/Enum.html'
'File,DataInputStream,DataOutputStream,InetAddress,Socket,Closeable,BufferedReader,BufferedWriter'.split(',').forEach(name => {
	typeIndex[name] = oracle + 'io/'+name+'.html'
})

typeIndex['BufferedImage'] = oracle + 'awt/image/BufferedImage.html'
typeIndex['AtomicBoolean'] = oracle + 'util/concurrent/atomic/AtomicBoolean.html'
typeIndex['AtomicInteger'] = oracle + 'util/concurrent/atomic/AtomicInteger.html'
typeIndex['AtomicLong'] = oracle + 'util/concurrent/atomic/AtomicLong.html'

function formatType(typeName, ignored) {
	
	// console.log('formatting', typeName)
	
	if(typeName == 'Void') typeName = 'Unit' // weird
	if(typeName == 'Void?') typeName = 'Unit?' // weird
	if(typeName==''||typeName=='*') return typeName;
	
	if(typeName[0] == '(' && typeName[typeName.length-1] == ')'){
		return '('+formatType(typeName.substr(1,typeName.length-2), ignored)+')'
	}
	
	if(typeName[0] == '(' && endsWith(typeName,')?')){
		return '('+formatType(typeName.substr(1,typeName.length-3), ignored)+')?'
	}
	
	if(typeName[0] == '(' && typeName.indexOf(')->') > 0){
		// split them... (Map<A,B>,C)->X
		let i = typeName.indexOf(')->')
		let j = 1, k = j, depth = 0
		let params = []
		let name = null
		for(;k<i;k++){
			// : is a hack here, as the name is lowercase, and unlikely to be linked
			if((typeName[k]==':') && depth==0) {
				name=typeName.substring(j,k)
				j=k+1
			}
			if((typeName[k]==',') && depth==0) {
				params.push([name,typeName.substring(j,k)])
				name=null
				j=k+1
			}
			else if(typeName[k]=='<') depth++
			else if(typeName[k]=='>') depth--
		}
		params.push([name,typeName.substring(j,i)])
		return '(' + params.map(t => (t[0] ? t[0] + ': ' : '') + formatType(t[1], ignored)).join(', ') + ')<nobr>-&gt;</nobr>'+formatType(typeName.substring(i+3), ignored)
	}
	
	if(typeName.indexOf('<') > 0 && typeName.indexOf('>') > 0){
		let i = typeName.indexOf('<'), e = typeName.lastIndexOf('>')
		let j = i+1, k = j, depth = 0
		let params = []
		for(;k<e;k++){
			if(typeName[k]==',' && depth==0) {
				params.push(typeName.substring(j,k))
				j=k+1
			}
			else if(typeName[k]=='<') depth++
			else if(typeName[k]=='>') depth--
		}
		params.push(typeName.substring(j,e))
		return formatType(typeName.substring(0,i), ignored) + '&lt;' + params.map(t => formatType(t, ignored)).join(', ') + '&gt;' + typeName.substr(e+1)
	}
	
	if(typeName.indexOf('.') >= 0) {
		return typeName.split('.').map(t => formatType(t,ignored)).join('.')
	}
	
	let simpleName = typeName.split('?')[0].split('<')[0]
	let link = typeIndex[simpleName]
	let isIgnored = Array.isArray(ignored) && ignored.indexOf(simpleName)>=0
	if(!link && !isIgnored) console.log('Unknown type:', simpleName)
	let escapedName = typeName.split('<').join('&lt;').split('>').join('&gt;')
	let fullLink = link && !isIgnored && (link.indexOf('https://') == 0 ? link : baseURL + '?page='+typeIndex[simpleName])
	return link ? '<a class="type" href="' + fullLink + '">' + escapedName + '</a>' : isIgnored ? '<span class="generic">' + escapedName + '</span>' : escapedName
}

function formatCommentLinks(c) {
	let i = 0
	let r = ''
	while(true){
		let j0 = c.indexOf('https://', i)
		let j1 = c.indexOf('http://', i)
		let j = Math.min(j0<0?c.length:j0, j1<0?c.length:j1)
		if(j >= c.length) break
		// find end of link... ], space, \t\r\n
		let endChars = ' \t\r\n'
		if(c[j-1] == '[') endChars = ']'
		else if(c[j-1] == '(') endChars = ')'
		let k=j
		if(endChars.length == 1){
			k = c.indexOf(endChars, j)
			if(k<0) k = c.length
		} else {
			for(;k<c.length;k++) {
				if(endChars.indexOf(c[k]) >= 0) {
					break
				}
			}
		}
		// [title](url)
		if(c[j-2] == ']' && c[j-1] == '(' && c.lastIndexOf('[',j-2) >= i && c[k] == ')') {
			let q = c.lastIndexOf('[',j-2)
			r += c.substring(i, q)
			let url = c.substring(j, k)
			r += '<a href="' + url + '">' + c.substring(q+1,j-2) + '</a>'
			k++ // skip )
		} else {
			r += c.substring(i, j)
			let url = c.substring(j, k)
			r += '<a href="' + url + '">' + url + '</a>'
		}
		i = k
	}
	if(i == 0) return c
	return r + c.substring(i)
}

function formatComment(c) {
	// make @param, @throws and such bold
	return c.split('\n').map(formatCommentLinks).join('<br>')
		.split('@param').join('<b>@param</b>')
		.split('@throws').join('<b>@throws</b>')
		.split('@return').join('<b>@return</b>')
		.split('@author').join('<b>@author</b>')
}

function formatGenerics(g, ignored){
	let gi = g.indexOf(':')
	return gi <= 0 ? 
		'<span class="generic">' + g + '</span>' :
		'<span class="generic">' + g.substr(0,gi) + '</span>: ' + formatType(g.substr(gi+1), ignored)
}

function displayClass(dataK, path, subPath, key) {
	searchResultsSec.style.display = 'none'
	docs.style.display = ''
	let kw = dataK.k || []
	let generics7 = dataK.g || []
	let generics = generics7.map(g => g.split(':')[0]) // remove type info, now it's just for formatType(,ignored)
	title.innerHTML = '<span class="keyword">' + kw.filter(isKeyword).join(' ') + '</span> ' + key +
		(generics.length ? '&lt;' + generics7.map(g => formatGenerics(g, generics)).join(', ') + '&gt;': '')
	classDocs.innerHTML = ''
	kw.filter(k => k[0] == '@').forEach(k => {
		let pi = document.createElement('p')
		pi.classList.add('annotation')
		pi.innerHTML = k
		classDocs.appendChild(pi)
	})
	kw.filter(k => k[0] == '*').forEach(k => {
		let pi = document.createElement('p')
		pi.classList.add('comment')
		pi.innerHTML = formatComment(k.substr(1))
		classDocs.appendChild(pi)
	})
	let linkParentPath = key == 'Companion' || !isNaN(dataK.o)
	let subPath1 = linkParentPath ? path : subPath
	let prefix = ["src","test/src","KOML/src", "SDF/src", "Bullet/src", "Box2D/src", "Recast/src", "Lua/src", "PDF/src"][dataK.p]
	githubLink.href = 'https://github.com/AntonioNoack/RemsEngine/blob/master/' + prefix + '/' + subPath1 + (isKtFile(kw,dataK) ? '.kt' : '/')
	superClasses.innerHTML = ''
	if(dataK.s) {
		superClassTitle.style.display = ''
		superClassTitle.innerText = isNaN(dataK.o) ? 'Super Classes' : 'Class'
		dataK.s.forEach((type) => {
			if(superClasses.innerText.length > 0) superClasses.innerHTML += ", "
			superClasses.innerHTML += '<b class="type">' + formatType(type) + '</b>'
		})
	} else superClassTitle.style.display = 'none'
	childClasses.innerHTML = ''
	if(dataK.c) {
		childClassTitle.style.display = ''
		childClassTitle.innerText = kw.indexOf('enum') >= 0 ? 'Values' : 'Child Classes'
		dataK.c.forEach((type) => {
			if(childClasses.innerText.length > 0) childClasses.innerHTML += ", "
			childClasses.innerHTML += '<b class="type">' + formatType(type) + '</b>'
		})
	} else childClassTitle.style.display = 'none'
	fields.innerHTML = ''
	if(!isNaN(dataK.o)){
		let ordinal = dataK.o
		let pi = document.createElement('p')
		pi.classList.add('comment')
		pi.innerHTML = 'Ordinal: ' + ordinal
		classDocs.appendChild(pi)
	}
	if(dataK.f) {
		fieldTitle.style.display = ''
		dataK.f.forEach((field) => {
			// name, keywords, type
			field[1].filter(k => k[0] == '*').forEach(k => {
				let pi = document.createElement('p')
				pi.classList.add('comment')
				pi.innerHTML = formatComment(k.substr(1))
				fields.appendChild(pi)
			})
			field[1].filter(k => k[0] == '@').forEach(k => {
				let pi = document.createElement('p')
				pi.classList.add('annotation')
				pi.innerHTML = k
				fields.appendChild(pi)
			})
			let p = document.createElement('p')
			p.innerHTML += '<span class="keyword">' + field[1].filter(isKeyword).join(' ') + '</span> <span class="name">' + field[0] + '</span>: <span class="type">' + formatType(field[2]||'?') + '</span>'
			fields.appendChild(p)
		})
	} else fieldTitle.style.display = 'none'
	methods.innerHTML = ''
	if(dataK.m) {
		methodTitle.style.display = ''
		dataK.m.forEach((method) => {
			// name, params, keywords, retType
			let keywords = method[3]
			keywords.filter(k => k[0] == '*').forEach(k => {
				let pi = document.createElement('p')
				pi.classList.add('comment')
				pi.innerHTML = formatComment(k.substr(1))
				methods.appendChild(pi)
			})
			keywords.filter(k => k[0] == '@').forEach(k => {
				let pi = document.createElement('p')
				pi.classList.add('annotation')
				pi.innerHTML = k
				methods.appendChild(pi)
			})
			let name = method[0]
			let nameI = name.indexOf('.')
			let prefixClass = null
			if(nameI >= 0){
				prefixClass = name.substr(0,nameI)
				name = name.substr(nameI+1)
			}
			let isConstructor = name == ''
			let p = document.createElement('p')
			let params = method[1]
			let generics0 = method[2]
			let generics1 = generics.concat(generics0.map(g => g.split(':')[0]))
			let retType = method[4]
			p.innerHTML += '<span class="keyword">' + keywords.filter(isKeyword).join(' ') + 
				(isConstructor ? ' constructor' : ' fun') + '</span>' + 
				(generics0.length ? ' &lt;' + generics0.map(g => formatGenerics(g,generics1)).join(', ').split('<span class="generic">reified</span>,').join('<span class="keyword">reified</span>') + '&gt;' : '') + (isConstructor ? '' : ' ' + (prefixClass ? formatType(prefixClass, generics1) + '.' : '') + 
				'<span class="name">' + name + '</span>') + '(' +
				([...Array(params.length/2).keys()].map(i => '<span class="param">' + params[i*2] + '</span>: <span class="type">' + formatType(params[i*2+1], generics1) + '</span>').join(', ')) +
				((isConstructor || retType == 'Unit') ? ')' : '): <span class="type">' + formatType(retType||'?', generics1) + '</span>')
			methods.appendChild(p)
		})
	} else methodTitle.style.display = 'none'
	companionTitle.innerText = key == 'Companion' ? [subPath.split('/')].map(x => x[x.length-2])[0] : dataK.Companion ? 'Companion' : ''
	companionTitle.href = key == 'Companion' ? '?page=' + path : dataK.Companion ? '?page=' + subPath + '/Companion' : ''
	companionTitle.onclick = (e) => {
		openPath(companionTitle.href.split('=')[1])
		return false // prevent default and propagation
	}
	dataK?.x?.()
	let subPath2 = new URLSearchParams(window.location.search).get('page')
	if(subPath2 != subPath) window.history.pushState(subPath, null, window.location.href.split('?')[0] + "?page=" + subPath)
}

function isKtFile(kw,dataK) {
	return kw.indexOf('object') >= 0 || kw.indexOf('companion') >= 0 ||
		kw.indexOf('interface') >= 0 || !isNaN(dataK.o) || kw.indexOf('companion') >= 0 ||
		kw.indexOf('class') >= 0 || kw.indexOf('enum') >= 0;
}

function createTree(ul, data, depth, path) {
	let sortedProperties = sortObject(data)
	let unfoldAutomatically = sortedProperties.length == 1 || depth == 0;
	sortedProperties.forEach((key0) => {
		
		let key = key0
		let li = document.createElement('li')
		let lis = document.createElement('span')
		lis.innerText = key
		li.appendChild(lis)
		let dataK = data[key]
		let kw = dataK.k || []
		
		if(kw.indexOf('object') >= 0 || !isNaN(dataK.o)) li.classList.add('ktObject')
		if(kw.indexOf('interface') >= 0) li.classList.add('ktInterface')
		if(kw.indexOf('companion') >= 0) li.classList.add('ktCompanion')
		if(kw.indexOf('class') >= 0) li.classList.add('ktClass')
		if(kw.indexOf('enum') >= 0) li.classList.add('ktEnum')

		let subPath = path ? path + '/' + key : key

		function unfold() {
			li.classList.add('down')
			childList.style.display = ''
			if(childList.children.length == 0) {
				createTree(childList, data[key], depth+1, subPath)
			}
		}
		
		dataK.q = () => {
			if(childList.style.display == 'none' || childList.children.length == 0) {
				unfold()
			}
		}
		
		dataK.x = () => {
			lastCurrent?.classList?.remove?.('current')
			lis.classList.add('current')
			lastCurrent = lis
		}
		
		let childList = document.createElement('ul')
		childList.style.display = 'none'
		li.appendChild(childList)
		if(unfoldAutomatically) unfold()
		li.onclick = (e) => {
			if(childList.style.display == 'none' || childList.children.length == 0) {
				unfold()
				displayClass(dataK, path, subPath, key)
			} else {
				li.classList.remove('down')
				childList.style.display = 'none'
			}
			e.stopPropagation()
		}
		ul.appendChild(li)
	})
}
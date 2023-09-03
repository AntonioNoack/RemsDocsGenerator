
let maxNumResults = 250
let baseURL = window.location.href.split('?')[0]
let typeIndex = {}

async function unzipDocs() {
	const {entries} = await unzipit.unzip('docs.zip');
	Object.values(entries).map(async(entry) => {
	
		let data = window.data = await entry.json()
		tree.innerHTML = '' // clear static tree
		createTypeIndex(data, null)
		createTree(tree, data, 0, null)
		
		let firstPage = new URLSearchParams(window.location.search).get('page') || 'me/anno/Engine'
		openPath(firstPage.split('.').join('/'))
	});
}
unzipDocs()

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

searchBar.onkeydown = (e) => {
	if(e.keyCode == 13) {
		let term = searchBar.value.trim().toLowerCase()
		if(term.length) {
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
						r.push(
							endsWith(r[0].toLowerCase(), end)?2:
							r[0].toLowerCase().indexOf(end)>=0?1.5:-1/r[1].length
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
						let i = 0
						let ril = ri
							.split('\n').join('\\n')
							.split('<b>').join('')
							.split('</b>').join('')
							.toLowerCase()
						let res = ''
						while(true){
							let j = ril.indexOf(term,i)
							if(j >= 0) {
								res += ri.substr(i,j-i)
								res += '<b>'+ri.substr(j,term.length)+'</b>'
								i = j+term.length
							} else {
								res += ri.substr(i)
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
		}
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
			if(key.length > 1 && key.toLowerCase().indexOf(term) >= 0) {
				let v = [pathI, key]
				addResult(result, v)
			}
			search(data[key], term, pathI, result)
		}
	} else if(typeof data === 'string' || data instanceof String) {
		if(data.toLowerCase().indexOf(term) >= 0) {
			let v = [path, data]
			addResult(result, v)
		}
	}
}

function addResult(result, v) {
	for(let i=0;i<result.length;i++){
		if(result[i][0] == v[0]) {
			if(result[i][1].indexOf(v[1]) < 0) result[i][1].push(v[1])
			return;
		}
	}
	result.push([v[0],[v[1]]])
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
	'kotlin.collections': 'Map,List,Set,HashSet,HashMap,ArrayList,LinkedList,MutableList,MutableSet,MutableMap,Iterator,Collection',
	'kotlin': 'Boolean,Byte,Short,Char,Int,Long,Float,Double,String,Unit,BooleanArray,ByteArray,ShortArray,CharArray,IntArray,LongArray,FloatArray,DoubleArray,Array,String,Unit,Any,Nothing,CharSequence,Exception,RuntimeException,Pair,Triple,Comparator',
	'kotlin.reflect': 'KClass',
	'kotlin.text': 'StringBuilder,Charset',
	'kotlin.io': 'InputStream,OutputStream'
}
for(key in stdlib){
	let baseURL = stdlibBase + key + '/'
	stdlib[key].split(',').forEach(name => {
		let name1 = name.split('').map(x => x == x.toLowerCase() ? x : '-'+x.toLowerCase()).join('')
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
'File,DataInputStream,DataOutputStream,InetAddress,Socket,Closeable'.split(',').forEach(name => {
	typeIndex[name] = oracle + 'io/'+name+'.html'
})

typeIndex['AtomicBoolean'] = oracle + 'util/concurrent/atomic/AtomicBoolean.html'
typeIndex['AtomicInteger'] = oracle + 'util/concurrent/atomic/AtomicInteger.html'
typeIndex['AtomicLong'] = oracle + 'util/concurrent/atomic/AtomicLong.html'

function formatType(typeName) {
	
	// console.log('formatting', typeName)
	
	if(typeName == 'Void') typeName = 'Unit' // weird
	if(typeName == 'Void?') typeName = 'Unit?' // weird
	
	if(typeName[0] == '(' && typeName[typeName.length-1] == ')'){
		return '('+formatType(typeName.substr(1,typeName.length-2))+')'
	}
	
	if(typeName[0] == '(' && endsWith(typeName,')?')){
		return '('+formatType(typeName.substr(1,typeName.length-3))+')?'
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
		return '(' + params.map(t => (t[0] ? t[0] + ':' : '') + formatType(t[1])).join(',') + ')-&gt;'+formatType(typeName.substring(i+3))
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
		return formatType(typeName.substring(0,i)) + '&lt;' + params.map(formatType).join(',') + '&gt;' + typeName.substr(e+1)
	}
	
	// todo types in generics could be linked
	// todo types in lambdas
	let simpleName = typeName.split('?')[0].split('<')[0]
	let link = typeIndex[simpleName]
	if(!link) console.log('Unknown type:', simpleName)
	let escapedName = typeName.split('<').join('&lt;').split('>').join('&gt;')
	let fullLink = link && (link.indexOf('https://') == 0 ? link : baseURL + '?page='+typeIndex[simpleName])
	return link ? '<a href="' + fullLink + '">' + escapedName + '</a>' : escapedName
}

function displayClass(dataK, path, subPath, key) {
	searchResultsSec.style.display = 'none'
	docs.style.display = ''
	let kw = dataK.k || []
	title.innerHTML = '<span class="keyword">' + kw.filter(isKeyword).join(' ') + '</span> ' + key +
		(dataK.g ? '&lt;' + dataK.g.join(', ') + '&gt;': '')
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
		pi.innerHTML = k.substr(1).split('\n').join('<br>')
		classDocs.appendChild(pi)
	})
	let subPath1 = key == 'Companion' ? path : subPath
	let prefix = ["src","KOML", "SDF", "Bullet", "Box2D", "Recast", "Lua", "PDF"][dataK.p]
	githubLink.href = 'https://github.com/AntonioNoack/RemsEngine/blob/master/' + prefix + '/' + subPath1 + (isKtFile(kw,dataK) ? '.kt' : '/')
	superClasses.innerHTML = ''
	if(dataK.s) dataK.s.forEach((type) => {
		if(superClasses.innerText.length > 0) superClasses.innerHTML += ", "
		superClasses.innerHTML += '<b class="type">' + formatType(type) + '</b>'
	})
	fields.innerHTML = ''
	if(!isNaN(dataK) || !isNaN(dataK.o)){
		let ordinal = !isNaN(dataK) ? dataK : dataK.o
		let pi = document.createElement('p')
		pi.classList.add('comment')
		pi.innerHTML = 'Ordinal: ' + ordinal
		classDocs.appendChild(pi)
		let typeName = subPath.split('/')
		typeName = typeName[typeName.length-2]
		superClasses.innerHTML += '<b class="type">' + formatType(typeName) + '</b>'
	}
	if(dataK.f) dataK.f.forEach((field) => {
		// name, keywords, type
		field[1].filter(k => k[0] == '*').forEach(k => {
			let pi = document.createElement('p')
			pi.classList.add('comment')
			pi.innerHTML = k.substr(1).split('\n').join('<br>')
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
	methods.innerHTML = ''
	if(dataK.m) dataK.m.forEach((method) => {
		// name, params, keywords, retType
		method[2].filter(k => k[0] == '*').forEach(k => {
			let pi = document.createElement('p')
			pi.classList.add('comment')
			pi.innerHTML = k.substr(1).split('\n').join('<br>')
			methods.appendChild(pi)
		})
		method[2].filter(k => k[0] == '@').forEach(k => {
			let pi = document.createElement('p')
			pi.classList.add('annotation')
			pi.innerHTML = k
			methods.appendChild(pi)
		})
		let isConstructor = method[0] == ''
		let p = document.createElement('p')
		let params = method[1]
		p.innerHTML += '<span class="keyword">' + method[2].filter(isKeyword).join(' ') + (isConstructor ? ' constructor' : ' fun') + '</span>' + (isConstructor ? '' : ' <span class="name">' + method[0] + '</span>') + '(' +
		[...Array(params.length/2).keys()].map(i => '<span class="param">' + params[i*2] + '</span>: <span class="type">' + formatType(params[i*2+1]) + '</span>').join(', ') + 
		(isConstructor ? ')' : '): <span class="type">' + formatType(method[3]||'?') + '</span>')
		methods.appendChild(p)
	})
	window.history.replaceState(subPath, key, window.location.href.split('?')[0] + "?page=" + subPath)
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
		li.innerHTML = '<span>' + key + '</span>'
		let dataK = data[key]
		let kw = dataK.k || []
		
		if(kw.indexOf('object') >= 0 || kw.indexOf('companion') >= 0) li.classList.add('ktObject')
		if(kw.indexOf('interface') >= 0 || !isNaN(dataK.o)) li.classList.add('ktObject')
		if(kw.indexOf('companion') >= 0) li.classList.add('ktCompanion')
		if(kw.indexOf('class') >= 0) li.classList.add('ktClass')
		if(kw.indexOf('enum') >= 0) li.classList.add('ktEnum')

		let subPath = path ? path + '/' + key : key

		function unfold(){
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
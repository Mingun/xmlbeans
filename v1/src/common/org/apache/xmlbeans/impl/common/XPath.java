/*
* The Apache Software License, Version 1.1
*
*
* Copyright (c) 2000-2003 The Apache Software Foundation.  All rights 
* reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
*
* 1. Redistributions of source code must retain the above copyright
*    notice, this list of conditions and the following disclaimer. 
*
* 2. Redistributions in binary form must reproduce the above copyright
*    notice, this list of conditions and the following disclaimer in
*    the documentation and/or other materials provided with the
*    distribution.
*
* 3. The end-user documentation included with the redistribution,
*    if any, must include the following acknowledgment:  
*       "This product includes software developed by the
*        Apache Software Foundation (http://www.apache.org/)."
*    Alternately, this acknowledgment may appear in the software itself,
*    if and wherever such third-party acknowledgments normally appear.
*
* 4. The names "Apache" and "Apache Software Foundation" must 
*    not be used to endorse or promote products derived from this
*    software without prior written permission. For written 
*    permission, please contact apache@apache.org.
*
* 5. Products derived from this software may not be called "Apache 
*    XMLBeans", nor may "Apache" appear in their name, without prior 
*    written permission of the Apache Software Foundation.
*
* THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
* OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
* ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
* USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
* OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
* SUCH DAMAGE.
* ====================================================================
*
* This software consists of voluntary contributions made by many
* individuals on behalf of the Apache Software Foundation and was
* originally based on software copyright (c) 2000-2003 BEA Systems 
* Inc., <http://www.bea.com/>. For more information on the Apache Software
* Foundation, please see <http://www.apache.org/>.
*/

package org.apache.xmlbeans.impl.common;

import javax.xml.namespace.QName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.impl.common.XMLChar;

public class XPath
{
    public static class XPathCompileException extends XmlException
    {
        XPathCompileException ( XmlError err )
        {
            super( err.toString(), null, err );
        }
    }

    //
    //
    //

    public static class ExecutionContext
    {
        public ExecutionContext ( )
        {
            _stack = new ArrayList();
        }

        public static final int HIT     = 0x1;
        public static final int DESCEND = 0x2;
        public static final int ATTRS   = 0x4;
        
        public final void init ( XPath xpath )
        {
            if (_xpath != xpath)
            {
                _xpath = xpath;
                
                _paths = new PathContext [ xpath._selector._paths.length ];
                
                for ( int i = 0 ; i < _paths.length ; i++ )
                    _paths[ i ] = new PathContext();
            }

            _stack.clear();

            for ( int i = 0 ; i < _paths.length ; i++ )
                _paths[ i ].init( xpath._selector._paths[ i ] );
        }

        public final int start ( )
        {
            int result = 0;
            
            for ( int i = 0 ; i < _paths.length ; i++ )
                result |= _paths[ i ].start();

            return result;
        }
        
        public final int element ( QName name )
        {
            assert name != null;
            
            _stack.add( name );
            
            int result = 0;
            
            for ( int i = 0 ; i < _paths.length ; i++ )
                result |= _paths[ i ].element( name );

            return result;
        }
        
        public final boolean attr ( QName name )
        {
            boolean hit = false;
            
            for ( int i = 0 ; i < _paths.length ; i++ )
                hit = hit | _paths[ i ].attr( name );

            return hit;
        }

        public final void end ( )
        {
            _stack.remove( _stack.size() - 1 );
            
            for ( int i = 0 ; i < _paths.length ; i++ )
                _paths[ i ].end();
        }
        
        private final class PathContext
        {
            PathContext ( )
            {
                _prev = new ArrayList();
            }
            
            void init ( Step steps )
            {
                _curr = steps;
                _prev.clear();
            }

            private QName top ( int i )
            {
                return (QName) ExecutionContext.this._stack.get( _stack.size() - 1 - i );
            }

            private void backtrack ( )
            {
                assert _curr != null;
                
                if (_curr._hasBacktrack)
                {
                    _curr = _curr._backtrack;
                    return;
                }

                assert !_curr._deep;

                _curr = _curr._prev;

                search: for ( ; !_curr._deep ; _curr = _curr._prev )
                {
                    int t = 0;
                    
                    for ( Step s = _curr ; !s._deep ; s = s._prev )
                    {
                        if (!s.match( top( t++ )))
                            continue search;
                    }

                    break;
                }
            }
            
            int start ( )
            {
                assert _curr != null;
                assert _curr._prev == null;

                if (_curr._name != null)
                    return _curr._flags;

                // If the steps consist on only a terminator, then the path can
                // only be '.'.  In this case, we get a hit, but there is
                // nothing else to match.  No need to backtrack.

                _curr = null;

                return HIT;
            }
            
            int element ( QName name )
            {
                _prev.add( _curr );

                if (_curr == null)
                    return 0;

                assert _curr._name != null;

                if (!_curr._attr && _curr.match( name ))
                {
                    if ((_curr = _curr._next)._name != null)
                        return _curr._flags;
                    
                    backtrack();
                    
                    return _curr == null ? HIT : HIT | _curr._flags;
                }

                for ( ; ; )
                {
                    backtrack();

                    if (_curr == null)
                        return 0;

                    if (_curr.match( name ))
                    {
                        _curr = _curr._next;
                        break;
                    }

                    if (_curr._deep)
                        break;
                }
                
                return _curr._flags;
            }
            
            boolean attr ( QName name )
            {
                return _curr != null && _curr._attr && _curr.match( name );
            }

            void end ( )
            {
                _curr = (Step) _prev.remove( _prev.size() - 1 );
            }
            
            private Step _curr;
            private List _prev;
        }

        private XPath         _xpath;
        private ArrayList     _stack;
        private PathContext[] _paths;
    }

    //
    //
    //

    public static XPath compileXPath ( String xpath )
        throws XPathCompileException
    {
        return compileXPath( xpath, "$this", null );
    }
    
    public static XPath compileXPath ( String xpath, String currentNodeVar )
        throws XPathCompileException
    {
        return compileXPath( xpath, currentNodeVar, null );
    }

    public static XPath compileXPath ( String xpath, Map namespaces )
            throws XPathCompileException
    {
        return compileXPath( xpath, "$this", namespaces );
    }
    
    public static XPath compileXPath (
        String xpath, String currentNodeVar, Map namespaces )
            throws XPathCompileException
    {
        return
            new CompilationContext( namespaces, currentNodeVar ).
                compile( xpath );
    }

    private static class CompilationContext
    {
        CompilationContext ( Map namespaces, String currentNodeVar )
        {
            assert
                _currentNodeVar == null ||
                    _currentNodeVar.startsWith( "$" );

            if (currentNodeVar == null)
                _currentNodeVar = "$this";
            else
                _currentNodeVar = currentNodeVar;

            _namespaces = new HashMap();
            
            _externalNamespaces =
                namespaces == null ? new HashMap() : namespaces;
        }

        XPath compile ( String expr ) throws XPathCompileException
        {
            _offset = 0;
            _line = 1;
            _column = 1;
            _expr = expr;

            return tokenizeXPath();
        }
        
        int currChar ( )
        {
            return currChar( 0 );
        }
        
        int currChar ( int offset )
        {
            return
                _offset + offset >= _expr.length()
                    ? -1
                    : _expr.charAt( _offset + offset );
        }
        
        void advance ( )
        {
            if (_offset < _expr.length())
            {
                char ch = _expr.charAt( _offset );
                
                _offset++;
                _column++;

                if (ch == '\r' || ch == '\n')
                {
                    _line++;
                    _column = 1;

                    if (_offset + 1 < _expr.length())
                    {
                        char nextCh = _expr.charAt( _offset + 1 );

                        if ((nextCh == '\r' || nextCh == '\n') && ch != nextCh)
                            _offset++;
                    }
                }
            }
        }

        void advance ( int count )
        {
            assert count >= 0;
            
            while ( count-- > 0 )
                advance();
        }
                
        boolean isWhitespace ( )
        {
            return isWhitespace( 0 );
        }
        
        boolean isWhitespace ( int offset )
        {
            int ch = currChar( offset );
            return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r';
        }

        boolean isNCNameStart ( )
        {
            return
                currChar() == -1
                    ? false :
                    XMLChar.isNCNameStart( currChar() );
        }
        
        boolean isNCName ( )
        {
            return
                currChar() == -1
                    ? false :
                    XMLChar.isNCName( currChar() );
        }
        
        boolean startsWith ( String s )
        {
            return startsWith( s, 0 );
        }
        
        boolean startsWith ( String s, int offset )
        {
            if (_offset + offset >= _expr.length())
                return false;
            
            return _expr.startsWith( s, _offset + offset );
        }

        private XPathCompileException newError ( String msg )
        {
            XmlError err =
                XmlError.forLocation(
                    msg, XmlError.SEVERITY_ERROR, null,
                    _line, _column, _offset );
                                     
            return new XPathCompileException( err );
        }

        String lookupPrefix ( String prefix ) throws XPathCompileException
        {
            if (_namespaces.containsKey( prefix ))
                return (String) _namespaces.get( prefix );
            
            if (_externalNamespaces.containsKey( prefix ))
                return (String) _externalNamespaces.get( prefix );

            if (prefix.equals( "xml" ))
                  return "http://www.w3.org/XML/1998/namespace";
            
            if (prefix.equals( "xs" ))
                  return "http://www.w3.org/2001/XMLSchema";
            
            if (prefix.equals( "xsi" ))
                  return "http://www.w3.org/2001/XMLSchema-instance";
            
            if (prefix.equals( "fn" ))
                  return "http://www.w3.org/2002/11/xquery-functions";

            throw newError( "Undefined prefix: " + prefix );
        }

        private boolean parseWhitespace ( ) throws XPathCompileException
        {
            boolean sawSpace = false;
            
            while ( isWhitespace() )
            {
                advance();
                sawSpace = true;
            }

            return sawSpace;
        }
        
        //
        // Tokenizing will consume whitespace followed by the tokens, separated
        // by whitespace.  The whitespace following the last token is not
        // consumed.
        //
        
        private boolean tokenize ( String s )
        {
            assert s.length() > 0;
            
            int offset = 0;

            while ( isWhitespace( offset ) )
                offset++;
            
            if (!startsWith( s, offset ))
                return false;

            offset += s.length();

            advance( offset );

            return true;
        }

        private boolean tokenize ( String s1, String s2 )
        {
            assert s1.length() > 0;
            assert s2.length() > 0;
            
            int offset = 0;

            while ( isWhitespace( offset ) )
                offset++;
            
            if (!startsWith( s1, offset ))
                return false;

            offset += s1.length();

            while ( isWhitespace( offset ) )
                offset++;
            
            if (!startsWith( s2, offset ))
                return false;
                
            offset += s2.length();

            advance( offset );

            return true;
        }

        private boolean tokenize ( String s1, String s2, String s3 )
        {
            assert s1.length() > 0;
            assert s2.length() > 0;
            assert s3.length() > 0;
            
            int offset = 0;

            while ( isWhitespace( offset ) )
                offset++;
            
            if (!startsWith( s1, offset ))
                return false;

            offset += s1.length();

            while ( isWhitespace( offset ) )
                offset++;
            
            if (!startsWith( s2, offset ))
                return false;
                
            offset += s2.length();

            while ( isWhitespace( offset ) )
                offset++;
            
            if (!startsWith( s3, offset ))
                return false;
            
            offset += s3.length();
                
            advance( offset );

            return true;
        }

        private String tokenizeNCName ( ) throws XPathCompileException
        {
            parseWhitespace();
            
            if (!isNCNameStart())
                throw newError( "Expected non-colonized name" );

            StringBuffer sb = new StringBuffer();

            sb.append( (char) currChar() );

            for ( advance() ; isNCName() ; advance() )
                sb.append( (char) currChar() );

            return sb.toString();
        }

        private QName getAnyQName ( )
        {
            return new QName( "", "" );
        }
        
        private QName tokenizeQName ( ) throws XPathCompileException
        {
            if (tokenize( "*" ))
                return getAnyQName();
            
            String ncName = tokenizeNCName();

            if (!tokenize( ":" ))
                return new QName( lookupPrefix( "" ), ncName );
            
            return
                new QName(
                    lookupPrefix( ncName ),
                    tokenize( "*" ) ? "" : tokenizeNCName() );
        }
        
        private String tokenizeQuotedUri ( ) throws XPathCompileException
        {
            char quote;
            
            if (tokenize( "\"" ))
                quote = '"';
            else  if (tokenize( "'" ))
                quote = '\'';
            else
                throw newError( "Expected quote (\" or ')" );

            StringBuffer sb = new StringBuffer();

            for ( ; ; )
            {
                if (currChar() == -1)
                    throw newError( "Path terminated in URI literal" );
                
                if (currChar() == quote)
                {
                    advance();
                    
                    if (currChar() != quote)
                        break;
                }
                
                sb.append( (char) currChar() );
                
                advance();
            }

            return sb.toString();
        }

        private Step addStep ( boolean deep, boolean attr, QName name, Step steps )
        {
            Step step = new Step( deep, attr, name );

            if (steps == null)
                return step;

            Step s = steps;
            
            while ( steps._next != null )
                steps = steps._next;

            steps._next = step;
            step._prev = steps;

            return s;
        }

        private Step tokenizeSteps ( ) throws XPathCompileException
        {
            if (tokenize( "/" ))
                throw newError( "Absolute paths unsupported" );

            boolean deep;

            if (tokenize( "$", _currentNodeVar, "//" ) || tokenize( ".", "//" ))
                deep = true;
            else if (tokenize( "$", _currentNodeVar, "/" ) || tokenize( ".", "/" ))
                deep = false;
            else if (tokenize( "$", _currentNodeVar ) || tokenize( "." ))
                return addStep( false, false, null, null );
            else
                deep = false;

            Step steps = null;

            // Compile the steps removing /. and mergind //. with the next step

            boolean deepDot = false;

            for ( ; ; )
            {
                if (tokenize( "attribute", "::" ) || tokenize( "@" ))
                {
                    steps = addStep( deep, true, tokenizeQName(), steps );
                    break;
                }

                QName name;
                
                if (tokenize( "." ))
                    deepDot = deepDot || deep;
                else if (tokenize( "child", "::" ) && (name = tokenizeQName()) != null)
                    steps = addStep( deep, false, name, steps );
                else if ((name = tokenizeQName()) != null)
                    steps = addStep( deep, false, name, steps );

                if (tokenize( "//" ))
                {
                    deep = true;
                    deepDot = false;
                }
                else if (tokenize( "/" ))
                {
                    if (deepDot)
                        deep = true;
                }
                else
                    break;
            }

            // If there was a //. at the end of th path, then we need to make
            // two paths, one with * at the end and another with @* at the end.

            if ((_lastDeepDot = deepDot))
            {
                _lastDeepDot = true;
                steps = addStep( true, false, getAnyQName(), steps );
            }

            // Add sentinal step (_name == null)
            
            return addStep( false, false, null, steps );
        }

        private void computeBacktrack ( Step steps )
            throws XPathCompileException
        {
            //
            // Compute static backtrack information
            //
            // Note that I use the fact that _hasBacktrack is initialized to
            // false and _backtrack to null in the following code.
            //

            Step s, t;
            
            for ( s = steps ; s != null ; s = t )
            {
                // Compute the segment from [ s, t )
                
                for ( t = s._next ; t != null && !t._deep ; )
                    t = t._next;

                // If the segment is NOT rooted at //, then the backtrack is
                // null for the entire segment, including possible attr and/or
                // sentinal

                if (!s._deep)
                {
                    for ( Step u = s ; u != t ; u = u._next )
                        u._hasBacktrack = true;

                    continue;
                }

                // Compute the sequence [ s, u ) of length n which contain no
                // wild steps.

                int n = 0;
                Step u = s;

                while ( u != t && u._name != null && !u.isWild() && !u._attr )
                {
                    n++;
                    u = u._next;
                }

                // Now, apply KMP to [ s, u ) for fast backtracking

                QName [] pattern = new QName [ n + 1 ];
                int [] kmp = new int [ n + 1 ];

                Step v = s;
                
                for ( int i = 0 ; i < n ; i++ )
                {
                    pattern[ i ] = v._name;
                    v = v._next;
                }

                pattern[ n ] = getAnyQName();

                int i = 0;
                int j = kmp[ 0 ] = -1;

                while ( i < n )
                {
                    while ( j > -1 && !pattern[ i ].equals( pattern[ j ] ) )
                        j = kmp[ j ];

                    if (pattern[ ++i ].equals( pattern[ ++j ] ))
                        kmp[ i ] = kmp[ j ];
                    else
                        kmp[ i ] = j;
                }

                i = 0;
                
                for ( v = s ; v != u ; v = v._next )
                {
                    v._hasBacktrack = true;
                    v._backtrack = s;
                    
                    for ( j = kmp[ i ] ; j > 0 ; j-- )
                        v._backtrack = v._backtrack._next;
                    
                    i++;
                }

                // Compute the success backtrack and stuff it into an attr and
                // sentinal if they exist for this segment
                
                v = s;

                if (n > 1)
                {
                    for ( j = kmp[ n - 1 ] ; j > 0 ; j-- )
                        v = v._next;
                }

                if (u != t && u._attr)
                {
                    u._hasBacktrack = true;
                    u._backtrack = v;
                    u = u._next;
                }

                if (u != t && u._name == null)
                {
                    u._hasBacktrack = true;
                    u._backtrack = v;
                }

                // The first part of a deep segment always backtracks to itself
                
                assert s._deep;

                s._hasBacktrack = true;
                s._backtrack = s;
            }
        }

        private void tokenizePath ( ArrayList paths )
            throws XPathCompileException
        {
            _lastDeepDot = false;
            
            Step steps = tokenizeSteps();
            
            computeBacktrack( steps );

            paths.add( steps );

            // If the last path ended in //., that path will match all
            // elements, here I make a path which matches all attributes.

            if (_lastDeepDot)
            {
                _sawDeepDot = true;
                
                Step s = null;

                for ( Step t = steps ; t != null ; t = t._next )
                {
                    if (t._next != null && t._next._next == null)
                        s = addStep( t._deep, true, t._name, s );
                    else
                        s = addStep( t._deep, t._attr, t._name, s );
                }

                computeBacktrack( s );

                paths.add( s );
            }
        }
        
        private Selector tokenizeSelector ( ) throws XPathCompileException
        {
            ArrayList paths = new ArrayList();

            tokenizePath( paths );

            while ( tokenize( "|" ) )
                tokenizePath( paths );

            return new Selector( (Step[]) paths.toArray( new Step [ 0 ] ) );
        }

        private XPath tokenizeXPath ( ) throws XPathCompileException
        {
            for ( ; ; )
            {
                if (tokenize( "declare", "namespace" ))
                {
                    if (!parseWhitespace())
                        throw newError( "Expected prefix after 'declare namespace'" );

                    String prefix = tokenizeNCName();

                    if (!tokenize( "=" ))
                        throw newError( "Expected '='" );

                    String uri = tokenizeQuotedUri();
                    
                    if (_namespaces.containsKey( prefix ))
                    {
                        throw newError(
                            "Redefinition of namespace prefix: " + prefix );
                    }

                    _namespaces.put( prefix, uri );

                    continue;
                }
                
                if (tokenize( "default", "element", "namespace" ))
                {
                    if (!tokenize( "=" ))
                        throw newError( "Expected '='" );
                    
                    String uri = tokenizeQuotedUri();
                    
                    if (_namespaces.containsKey( "" ))
                    {
                        throw newError(
                            "Redefinition of default element namespace" );
                    }

                    _namespaces.put( "", uri );

                    continue;
                }
                
                break;
            }

            // Add the default prefix mapping if it has not been redefined
            
            if (!_namespaces.containsKey( "" ))
                _namespaces.put( "", "" );

            Selector selector = tokenizeSelector();

            parseWhitespace();
            
            if (currChar() != -1)
            {
                throw newError(
                    "Unexpected char '" + (char) currChar() + "'" );
            }

            return new XPath( selector, _sawDeepDot );
        }

        private String _expr;

        private boolean _sawDeepDot;  // Saw one overall
        private boolean _lastDeepDot;

        private String _currentNodeVar;
        
        private Map _namespaces;
        private Map _externalNamespaces;
        
        private int _offset;
        private int _line;
        private int _column;
    }

    private static final class Step
    {
        Step ( boolean deep, boolean attr, QName name )
        {
            _name = name;

            _deep = deep;
            _attr = attr;

            int flags = 0;

            if (_deep || !_attr)
                flags |= ExecutionContext.DESCEND;

            if (_attr)
                flags |= ExecutionContext.ATTRS;

            _flags = flags;
        }

        boolean isWild ( )
        {
            return _name.getLocalPart().length() == 0;
        }

        boolean match ( QName name )
        {
            String local = _name.getLocalPart();

            if (local.length() == 0)
            {
                String uri = _name.getNamespaceURI();

                if (uri.length() == 0)
                    return true;

                return uri.equals( name.getNamespaceURI() );
            }

            return _name.equals( name );
        }

        final boolean _attr;
        final boolean _deep;

        int _flags;
        
        final QName _name;

        Step _next, _prev;

        boolean _hasBacktrack;
        Step    _backtrack;
    }

    private static final class Selector
    {
        Selector ( Step[] paths )
        {
            _paths = paths;
        }

        final Step[] _paths;
    }

    //
    //
    //
    
    private XPath ( Selector selector, boolean sawDeepDot )
    {
        _selector = selector;
        _sawDeepDot = sawDeepDot;
    }

    public boolean sawDeepDot ( )
    {
        return _sawDeepDot;
    }

    private final Selector _selector;
    private final boolean  _sawDeepDot;
}
# Introduction #

_**Secrets for Android**_ was initially available only in English because that is the language in which I am most fluent.  All translations into languages other than English have been very graciously done by volunteers.

# Translating Secrets into your Language #

If you would like to see _**Secrets for Android**_ available in your favourite language, why don't you help out?  Its quite easy.  Each language is structured as a simple text file, you can see some examples here:

English: http://code.google.com/p/secrets-for-android/source/browse/trunk/res/values/strings.xml<br>
French: <a href='http://code.google.com/p/secrets-for-android/source/browse/trunk/res/values-fr/strings.xml'>http://code.google.com/p/secrets-for-android/source/browse/trunk/res/values-fr/strings.xml</a><br>
Italian: <a href='http://code.google.com/p/secrets-for-android/source/browse/trunk/res/values-it/strings.xml'>http://code.google.com/p/secrets-for-android/source/browse/trunk/res/values-it/strings.xml</a><br>
Chinese: <a href='http://code.google.com/p/secrets-for-android/source/browse/trunk/res/values-zh-rCN/strings.xml'>http://code.google.com/p/secrets-for-android/source/browse/trunk/res/values-zh-rCN/strings.xml</a><br>
Chinese: <a href='http://code.google.com/p/secrets-for-android/source/browse/trunk/res/values-zh-rTW/strings.xml'>http://code.google.com/p/secrets-for-android/source/browse/trunk/res/values-zh-rTW/strings.xml</a><br>

To translate into a new language, <a href='http://secrets-for-android.googlecode.com/svn/trunk/res/values/strings.xml'>download</a> the English version and then modify it as needed.  Each string that requires translation looks like this:<br>
<br>
<pre><code>  &lt;string name="XXX"&gt;YYYY&lt;/string&gt;<br>
</code></pre>

Only change the YYYY part, leave the rest as is, with one exception: Inside the YYYY part, you may see strings of the form <code>{0}</code> or <code>{1,date,short}</code>.  Please do not translate these parts.<br>
<br>
Once the translation is complete, either send me an email with the translated <code>strings.xml</code> file, or <a href='http://code.google.com/p/secrets-for-android/issues/entry'>open a new issue</a> and attach the translation to it.<br>
<br>
If you would like to receive credit for your translation, please let me know.  Send me a short string with your name and a link to your website, preferably in the translated language.  See the home page for example.
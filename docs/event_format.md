# ActivityStreams

By default, EventBus uses [ActivityStreams 1.0](http://activitystrea.ms/specs/json/1.0/) as event format.  
ActivityStreams 1.0 specification defineds several concepts and fields of a activity. For more details check their [documentation](http://activitystrea.ms/specs/json/1.0/).

Summary:  
- It includes two parts here: Activity and Object.  
- A event can be present as a Activity, And a Activity could include multiple Objects.

## Conventions

**EventName:**    

- Use only lowercase letters, numbers, dots (.) and underscores (_);
- Prefix names with a namespace followed by a dot (e.g. order., user.*);
- End names with a verb that indicates what action it is (e.g. user.login, payment.subscribe). 

## Structure

### Activity

<table>
  <tr>
    <th>Property</th>
    <th>Value</th>
    <th>Description</th>
    <th>Mandatory?</th>
    <th>PHP Type</th>
  </tr>
  <tr>
    <td>id</td>
    <td>JSON [RFC4627] String</td>
    <td>Provides a permanent, universally unique identifier for the activity in the form of an absolute IRI [RFC3987]. An activity SHOULD contain a single id property. If an activity does not contain an id property, consumers MAY use the value of the url property as a less-reliable, non-unique identifier.</td>
    <td>y</td>
    <td>string</td>
  </tr>
  <tr>
    <td>title</td>
    <td>JSON [RFC4627] String</td>
    <td>Natural-language title or headline for the activity encoded as a single JSON String containing HTML markup. An activity MAY contain a title property.</td>
    <td>y</td>
    <td>string</td>
  </tr>
  <tr>
    <td>published</td>
    <td>[RFC3339] date-time</td>
    <td>The date and time at which the activity was published. An activity MUST contain a published property.</td>
    <td>y</td>
    <td>string</td>
  </tr>
  <tr>
    <td>verb</td>
    <td>JSON [RFC4627] String</td>
    <td>Identifies the action that the activity describes. An activity SHOULD contain a verb property whose value is a JSON String that is non-empty and matches either the "isegment-nz-nc" or the "IRI" production in [RFC3339]. Note that the use of a relative reference other than a simple name is not allowed. If the verb is not specified, or if the value is null, the verb is assumed to be "post".</td>
    <td>y</td>
    <td>string</td>
  </tr>
  <tr>
    <td>actor</td>
    <td>Object</td>
    <td>Describes the entity that performed the activity. An activity MUST contain one actor property whose value is a single Object.</td>
    <td>n</td>
    <td>ActivityObject</td>
  </tr>
  <tr>
    <td>object</td>
    <td>Object</td>
    <td>Describes the primary object of the activity. For instance, in the activity, "John saved a movie to his wishlist", the object of the activity is "movie". An activity SHOULD contain an object property whose value is a single Object. If the object property is not contained, the primary object of the activity MAY be implied by context.</td>
    <td>n</td>
    <td>ActivityObject</td>
  </tr>
  <tr>
    <td>target</td>
    <td>Object</td>
    <td>Describes the target of the activity. The precise meaning of the activity's target is dependent on the activities verb, but will often be the object the English preposition "to". For instance, in the activity, "John saved a movie to his wishlist", the target of the activity is "wishlist". The activity target MUST NOT be used to identity an indirect object that is not a target of the activity. An activity MAY contain a target property whose value is a single Object.</td>
    <td>n</td>
    <td>ActivityObject</td>
  </tr>
  <tr>
    <td>provider</td>
    <td>Object</td>
    <td>Describes the application that published the activity. Note that this is not necessarily the same entity that generated the activity. An activity MAY contain a provider property whose value is a single Object.</td>
    <td>n</td>
    <td>ActivityObject</td>
  </tr>
  <tr>
    <td>content</td>
    <td>JSON [RFC4627] String</td>
    <td>Natural-language description of the activity encoded as a single JSON String containing HTML markup. Visual elements such as thumbnail images MAY be included. An activity MAY contain a content property.</td>
    <td>n</td>
    <td>mixed</td>
  </tr>
  <tr>
    <td>generator</td>
    <td>Object</td>
    <td>Describes the application that generated the activity. An activity MAY contain a generator property whose value is a single Object.</td>
    <td>n</td>
    <td>ActivityObject</td>
  </tr>
</table>

### Object

<table>
  <tr>
    <th>Property</th>
    <th>Value</th>
    <th>Description</th>
    <th>Mandatory?</th>
    <th>PHP Type</th>
  </tr>
  <tr>
    <td>id</td>
    <td>JSON [RFC4627] String</td>
    <td>Provides a permanent, universally unique identifier for the object in the form of an absolute IRI [RFC3987]. An object SHOULD contain a single id property. If an object does not contain an id property, consumers MAY use the value of the url property as a less-reliable, non-unique identifier.</td>
    <td>n</td>
    <td>string</td>
  </tr>
  <tr>
    <td>objectType</td>
    <td>JSON [RFC4627] String</td>
    <td>Identifies the type of object. An object MAY contain an objectType property whose value is a JSON String that is non-empty and matches either the "isegment-nz-nc" or the "IRI" production in [RFC3987]. Note that the use of a relative reference other than a simple name is not allowed. If no objectType property is contained, the object has no specific type.</td>
    <td>n</td>
    <td>string</td>
  </tr>
  <tr>
    <td>attachments</td>
    <td>JSON [RFC4627] Array of Objects	</td>
    <td>A collection of one or more additional, associated objects, similar to the concept of attached files in an email message. An object MAY have an attachments property whose value is a JSON Array of Objects.</td>
    <td>n</td>
    <td>ActivityObject[]</td>
  </tr>
  <tr>
    <td>summary</td>
    <td>JSON [RFC4627] String</td>
    <td>Natural-language summarization of the object encoded as a single JSON String containing HTML markup. Visual elements such as thumbnail images MAY be included. An activity MAY contain a summary property.</td>
    <td>n</td>
    <td>mixed</td>
  </tr>
  <tr>
    <td>content</td>
    <td>JSON [RFC4627] String</td>
    <td>Natural-language description of the object encoded as a single JSON String containing HTML markup. Visual elements such as thumbnail images MAY be included. An object MAY contain a content property.</td>
    <td>n</td>
    <td>mixed</td>
  </tr>
  <tr>
    <td>downstreamDuplicates</td>
    <td>JSON [RFC4627] Array of Strings</td>
    <td>A JSON Array of one or more absolute IRI's [RFC3987] identifying objects that duplicate this object's content. An object SHOULD contain a downstreamDuplicates property when there are known objects, possibly in a different system, that duplicate the content in this object. This MAY be used as a hint for consumers to use when resolving duplicates between objects received from different sources.</td>
    <td>n</td>
    <td>string[]</td>
  </tr>
  <tr>
    <td>upstreamDuplicates</td>
    <td>JSON [RFC4627] Array of Strings</td>
    <td>A JSON Array of one or more absolute IRI's [RFC3987] identifying objects that duplicate this object's content. An object SHOULD contain an upstreamDuplicates property when a publisher is knowingly duplicating with a new ID the content from another object. This MAY be used as a hint for consumers to use when resolving duplicates between objects received from different sources.</td>
    <td>n</td>
    <td>string[]</td>
  </tr>
  <tr>
    <td>author</td>
    <td>Object</td>
    <td>Describes the entity that created or authored the object. An object MAY contain a single author property whose value is an Object of any type. Note that the author field identifies the entity that created the object and does not necessarily identify the entity that published the object. For instance, it may be the case that an object created by one person is posted and published to a system by an entirely different entity.</td>
    <td>n</td>
    <td>ActivityObject</td>
  </tr>
</table>

## User Cases (keep adding)

- Case1: benn logged in 

I will choose "user.login" as EventName, it following the conventions "namespace.verb"

<table>
  <tr>
    <th>title</th>
    <td>user.login</td>
  </tr>
  <tr>
    <th>verb</th>
    <td>login</td>
  </tr>
  <tr>
    <th>actor</th>
    <td>{ objectType: "user", id: 123456 }</td>
  </tr>    
  <tr>
    <th>id</th>
    <td>Generated Unique String</td>
  </tr>
  <tr>
    <th>published</th>
    <td>2017-10-13T11:31:34+08:00</td>
  </tr>
  <tr>
    <th>provider</th>
    <td>{ objectType: "community", id: "Poppen" }</td>
  </tr>
  <tr>
    <th>generator</th>
    <td>{ id: "tnc-event-dispatcher", content: { mode: "async", class: "UserLoginEvent" } }</td>
  </tr>
</table>

- Case2: benn visited fan's profile 

<table>
  <tr>
    <th>title</th>
    <td>user.visit</td>
  </tr>
  <tr>
    <th>verb</th>
    <td>visit</td>
  </tr>
  <tr>
    <th>actor</th>
    <td>{ objectType: "user", id: 12345 }</td>
  </tr>
  <tr>
    <th>object</th>
    <td>{ objectType: "profile", id: "fan" }</td>
  </tr>
  <tr>
    <th>id</th>
    <td>Generated Unique String</td>
  </tr>
  <tr>
    <th>published</th>
    <td>2017-10-13T11:31:34+08:00</td>
  </tr>
  <tr>
    <th>provider</th>
    <td>{ objectType: "community", id: "Poppen" }</td>
  </tr>
  <tr>
    <th>generator</th>
    <td>{ id: "tnc-event-dispatcher", content: { mode: "async", class: "UserLoginEvent" } }</td>
  </tr>
</table>


- Case3: benn send a message __to__ leo 

<table>
  <tr>
    <th>title</th>
    <td>message.send</td>
  </tr>
  <tr>
    <th>verb</th>
    <td>send</td>
  </tr>
  <tr>
    <th>actor</th>
    <td>{ objectType: "user", id: 12345 }</td>
  </tr>
  <tr>
    <th>object</th>
    <td>{ objectType: "message", id: 112231 }</td>
  </tr>
  <tr>
      <th>target</th>
      <td>{ objectType: "user", id: 88929 }</td>
    </tr>
  <tr>
    <th>id</th>
    <td>Generated Unique String</td>
  </tr>
  <tr>
    <th>published</th>
    <td>2017-10-13T11:31:34+08:00</td>
  </tr>
  <tr>
    <th>provider</th>
    <td>{ objectType: "community", id: "Poppen" }</td>
  </tr>
  <tr>
    <th>generator</th>
    <td>{ id: "tnc-event-dispatcher", content: { mode: "async", class: "UserLoginEvent" } }</td>
  </tr>
</table>